package com.freshproduce.pos.service;

import com.freshproduce.pos.domain.*;
import com.freshproduce.pos.dto.Requests.ConsignmentRequest;
import com.freshproduce.pos.dto.Responses.*;
import com.freshproduce.pos.exception.BusinessException;
import com.freshproduce.pos.exception.NotFoundException;
import com.freshproduce.pos.repository.ConsignmentAllocationRepository;
import com.freshproduce.pos.repository.ConsignmentRepository;
import com.freshproduce.pos.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Recording, listing and tracing consignments (FR-01, FR-03, FR-09). */
@Service
public class ConsignmentService {

    private final ConsignmentRepository consignmentRepository;
    private final ConsignmentAllocationRepository allocationRepository;
    private final StockMovementRepository movementRepository;
    private final FarmerService farmerService;
    private final ProductService productService;

    public ConsignmentService(ConsignmentRepository consignmentRepository,
                              ConsignmentAllocationRepository allocationRepository,
                              StockMovementRepository movementRepository,
                              FarmerService farmerService,
                              ProductService productService) {
        this.consignmentRepository = consignmentRepository;
        this.allocationRepository = allocationRepository;
        this.movementRepository = movementRepository;
        this.farmerService = farmerService;
        this.productService = productService;
    }

    /** Saves a consignment, assigns its unique reference and records the stock receipt. */
    @Transactional
    public ConsignmentDto record(ConsignmentRequest request, User recordedBy) {
        Farmer farmer = farmerService.find(request.farmerId());
        if (!"ACTIVE".equals(farmer.getStatus())) {
            throw new BusinessException("Farmer " + farmer.getName() + " is inactive and cannot supply consignments");
        }
        Product product = productService.find(request.productId());
        if (!product.isActive()) {
            throw new BusinessException("Product " + product.getName() + " is inactive. Select a valid product.");
        }
        if (request.dateReceived().isAfter(LocalDate.now())) {
            throw new BusinessException("Received date cannot be in the future");
        }

        BigDecimal quantity = request.quantityReceived().setScale(2, RoundingMode.HALF_UP);
        Consignment c = new Consignment();
        c.setFarmer(farmer);
        c.setProduct(product);
        c.setDateReceived(request.dateReceived());
        c.setQuantityReceived(quantity);
        c.setQuantityRemaining(quantity);
        c.setUnitPrice(request.unitPrice().setScale(2, RoundingMode.HALF_UP));
        c.setGrade(StringUtils.hasText(request.grade()) ? request.grade().trim() : null);
        c.setNotes(StringUtils.hasText(request.notes()) ? request.notes().trim() : null);
        c.setRecordedBy(recordedBy);
        c = consignmentRepository.save(c);
        c.setReferenceNumber(CodeGenerator.consignmentReference(c.getDateReceived(), c.getId()));

        StockMovement receipt = new StockMovement();
        receipt.setConsignment(c);
        receipt.setMovementType(MovementType.RECEIPT);
        receipt.setQuantity(quantity);
        receipt.setBalanceAfter(quantity);
        receipt.setReason("Consignment received from " + farmer.getName());
        receipt.setReference(c.getReferenceNumber());
        receipt.setMovementDate(c.getDateReceived().equals(LocalDate.now())
                ? LocalDateTime.now() : c.getDateReceived().atTime(7, 0));
        receipt.setUser(recordedBy);
        movementRepository.save(receipt);

        return ConsignmentDto.from(c);
    }

    @Transactional(readOnly = true)
    public List<ConsignmentDto> list(String status, Long productId, Long farmerId) {
        return consignmentRepository.findAllByOrderByDateReceivedDescIdDesc().stream()
                .filter(c -> !StringUtils.hasText(status) || status.equalsIgnoreCase(c.getStatus()))
                .filter(c -> productId == null || productId.equals(c.getProduct().getId()))
                .filter(c -> farmerId == null || farmerId.equals(c.getFarmer().getId()))
                .map(ConsignmentDto::from)
                .toList();
    }

    /** FR-09: search by unique reference and show farmer, product, received, sales history and remaining stock. */
    @Transactional(readOnly = true)
    public ConsignmentDetailDto findByReference(String reference) {
        Consignment c = consignmentRepository.findByReferenceNumberIgnoreCase(reference.trim())
                .orElseThrow(() -> new NotFoundException("No consignment found with reference " + reference.trim()));
        return detail(c);
    }

    @Transactional(readOnly = true)
    public ConsignmentDetailDto getDetail(Long id) {
        return detail(find(id));
    }

    public Consignment find(Long id) {
        return consignmentRepository.findById(id).orElseThrow(() -> new NotFoundException("Consignment not found"));
    }

    private ConsignmentDetailDto detail(Consignment c) {
        List<SaleHistoryLine> sales = allocationRepository.findByConsignment(c.getId()).stream()
                .map(a -> {
                    SaleItem item = a.getSaleItem();
                    Sale sale = item.getSale();
                    return new SaleHistoryLine(sale.getReceiptNumber(), sale.getSaleDate(),
                            sale.getAgent().getFullName(), a.getQuantity(), item.getUnitPrice(),
                            a.getQuantity().multiply(item.getUnitPrice()).setScale(2, RoundingMode.HALF_UP));
                })
                .toList();
        List<MovementDto> movements = movementRepository.findByConsignmentIdOrderByMovementDateAscIdAsc(c.getId())
                .stream().map(MovementDto::from).toList();

        BigDecimal sold = sales.stream().map(SaleHistoryLine::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal value = sales.stream().map(SaleHistoryLine::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal adjusted = movements.stream()
                .filter(m -> m.movementType() == MovementType.ADJUSTMENT)
                .map(m -> m.quantity().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ConsignmentDetailDto(ConsignmentDto.from(c), c.getFarmer().getContactNumber(),
                c.getFarmer().getAddress(), sold, adjusted, value, sales, movements);
    }
}
