package com.freshproduce.pos.service;

import com.freshproduce.pos.domain.*;
import com.freshproduce.pos.dto.Requests.SaleItemRequest;
import com.freshproduce.pos.dto.Requests.SaleRequest;
import com.freshproduce.pos.dto.Responses.*;
import com.freshproduce.pos.exception.BusinessException;
import com.freshproduce.pos.exception.InsufficientStockException;
import com.freshproduce.pos.exception.NotFoundException;
import com.freshproduce.pos.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * POS sale processing (UC-02, FR-02, FR-03, FR-04).
 *
 * <p>The whole sale – sale record, items, FIFO allocations, consignment balances, stock movements and
 * commission – is saved in ONE database transaction (NFR-03). If anything fails, everything is rolled back
 * and no partial stock records remain.</p>
 */
@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final ConsignmentRepository consignmentRepository;
    private final StockMovementRepository movementRepository;
    private final CommissionRepository commissionRepository;
    private final CommissionService commissionService;

    public SaleService(SaleRepository saleRepository, ProductRepository productRepository,
                       ConsignmentRepository consignmentRepository, StockMovementRepository movementRepository,
                       CommissionRepository commissionRepository, CommissionService commissionService) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.consignmentRepository = consignmentRepository;
        this.movementRepository = movementRepository;
        this.commissionRepository = commissionRepository;
        this.commissionService = commissionService;
    }

    @Transactional(rollbackFor = Exception.class)
    public SaleDto processSale(SaleRequest request, User agent) {
        return processSale(request, agent, LocalDateTime.now());
    }

    @Transactional(rollbackFor = Exception.class)
    public SaleDto processSale(SaleRequest request, User agent, LocalDateTime saleDate) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("A sale must contain at least one item");
        }
        LocalDate saleDay = saleDate.toLocalDate();

        // Steps 3-4: combine duplicate lines and check every product and its stock before changing anything.
        Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
        for (SaleItemRequest line : request.items()) {
            if (line.productId() == null || line.quantity() == null || line.quantity().signum() <= 0) {
                throw new BusinessException("Every sale item needs a product and a quantity greater than zero");
            }
            quantities.merge(line.productId(), line.quantity().setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        }
        Map<Long, Product> products = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : quantities.entrySet()) {
            Product product = productRepository.findById(entry.getKey())
                    .orElseThrow(() -> new BusinessException("Invalid product selected. Please select a valid product."));
            if (!product.isActive()) {
                throw new BusinessException(product.getName() + " is inactive. Please select a valid product.");
            }
            BigDecimal available = consignmentRepository.availableStock(product.getId(), saleDay);
            if (entry.getValue().compareTo(available) > 0) {
                throw new InsufficientStockException(String.format(
                        "Insufficient stock for %s: requested %s %s, only %s %s available",
                        product.getName(), entry.getValue().stripTrailingZeros().toPlainString(),
                        product.getUnitOfMeasure(), available.stripTrailingZeros().toPlainString(),
                        product.getUnitOfMeasure()));
            }
            products.put(product.getId(), product);
        }

        // Steps 6-7: build the sale and allocate stock using FIFO.
        Sale sale = new Sale();
        sale.setSaleDate(saleDate);
        sale.setAgent(agent);
        List<StockMovement> movements = new ArrayList<>();
        BigDecimal subTotal = BigDecimal.ZERO;

        for (Map.Entry<Long, BigDecimal> entry : quantities.entrySet()) {
            Product product = products.get(entry.getKey());
            BigDecimal quantity = entry.getValue();
            SaleItem item = new SaleItem();
            item.setProduct(product);
            item.setQuantity(quantity);
            item.setUnitPrice(product.getSellingPrice());
            item.setSubtotal(quantity.multiply(product.getSellingPrice()).setScale(2, RoundingMode.HALF_UP));
            allocateFifo(item, saleDate, agent, movements);
            sale.addItem(item);
            subTotal = subTotal.add(item.getSubtotal());
        }

        BigDecimal discount = request.discount() == null ? BigDecimal.ZERO
                : request.discount().setScale(2, RoundingMode.HALF_UP);
        if (discount.signum() < 0 || discount.compareTo(subTotal) > 0) {
            throw new BusinessException("Discount must be between R0.00 and the sale subtotal");
        }
        sale.setSubTotal(subTotal);
        sale.setDiscount(discount);
        sale.setTotalAmount(subTotal.subtract(discount));

        // Step 8: save the sale (items and allocations cascade), then the receipt number and movements.
        sale = saleRepository.save(sale);
        sale.setReceiptNumber(CodeGenerator.receiptNumber(saleDate, sale.getId()));
        for (StockMovement movement : movements) {
            movement.setReference(sale.getReceiptNumber());
            movementRepository.save(movement);
        }

        Commission commission = commissionService.recordCommission(sale);
        return toDto(sale, commission);
    }

    /**
     * FR-02: takes the quantity from the oldest eligible consignment first and only moves to the next
     * consignment when the older one is used up.
     */
    private void allocateFifo(SaleItem item, LocalDateTime saleDate, User agent, List<StockMovement> movements) {
        BigDecimal outstanding = item.getQuantity();
        List<Consignment> eligible = consignmentRepository.findEligibleForFifo(
                item.getProduct().getId(), saleDate.toLocalDate());
        for (Consignment consignment : eligible) {
            if (outstanding.signum() <= 0) {
                break;
            }
            BigDecimal take = consignment.getQuantityRemaining().min(outstanding);
            consignment.deduct(take);
            item.addAllocation(new ConsignmentAllocation(consignment, take));

            StockMovement movement = new StockMovement();
            movement.setConsignment(consignment);
            movement.setMovementType(MovementType.SALE);
            movement.setQuantity(take.negate());
            movement.setBalanceAfter(consignment.getQuantityRemaining());
            movement.setReason("POS sale");
            movement.setMovementDate(saleDate);
            movement.setUser(agent);
            movements.add(movement);

            outstanding = outstanding.subtract(take);
        }
        if (outstanding.signum() > 0) {
            throw new InsufficientStockException("Insufficient stock for " + item.getProduct().getName());
        }
    }

    @Transactional(readOnly = true)
    public SaleDto get(Long id) {
        Sale sale = saleRepository.findById(id).orElseThrow(() -> new NotFoundException("Sale not found"));
        return toDto(sale, commissionRepository.findBySaleId(id).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<SaleSummaryDto> list(LocalDate from, LocalDate to, User viewer) {
        boolean admin = viewer.getRole() == Role.ADMINISTRATOR;
        return saleRepository.findBySaleDateBetweenOrderBySaleDateDesc(from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay().minusNanos(1)).stream()
                .filter(s -> admin || s.getAgent().getId().equals(viewer.getId()))
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SaleSummaryDto> recent() {
        return saleRepository.findTop10ByOrderBySaleDateDesc().stream().map(this::toSummary).toList();
    }

    public SaleSummaryDto toSummary(Sale s) {
        String products = s.getItems().stream().map(i -> i.getProduct().getName()).collect(Collectors.joining(", "));
        BigDecimal commission = commissionRepository.findBySaleId(s.getId())
                .map(Commission::getCommissionAmount).orElse(BigDecimal.ZERO);
        return new SaleSummaryDto(s.getId(), s.getReceiptNumber(), s.getSaleDate(), s.getAgent().getFullName(),
                s.getItems().size(), products, s.getTotalAmount(), commission);
    }

    private SaleDto toDto(Sale sale, Commission commission) {
        List<SaleItemDto> items = sale.getItems().stream().map(i -> new SaleItemDto(
                i.getProduct().getId(), i.getProduct().getName(), i.getProduct().getUnitOfMeasure(),
                i.getQuantity(), i.getUnitPrice(), i.getSubtotal(),
                i.getAllocations().stream().map(a -> new AllocationDto(
                        a.getConsignment().getId(), a.getConsignment().getReferenceNumber(),
                        a.getConsignment().getFarmer().getName(), a.getConsignment().getDateReceived(),
                        a.getQuantity())).toList())).toList();
        return new SaleDto(sale.getId(), sale.getReceiptNumber(), sale.getSaleDate(), sale.getAgent().getFullName(),
                sale.getSubTotal(), sale.getDiscount(), sale.getTotalAmount(),
                commission == null ? null : commission.getCommissionRate(),
                commission == null ? null : commission.getCommissionAmount(), items);
    }
}
