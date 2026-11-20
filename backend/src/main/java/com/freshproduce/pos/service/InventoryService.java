package com.freshproduce.pos.service;

import com.freshproduce.pos.domain.*;
import com.freshproduce.pos.dto.Requests.StockAdjustmentRequest;
import com.freshproduce.pos.dto.Responses.MovementDto;
import com.freshproduce.pos.dto.Responses.StockSummaryDto;
import com.freshproduce.pos.exception.BusinessException;
import com.freshproduce.pos.repository.ConsignmentRepository;
import com.freshproduce.pos.repository.ProductRepository;
import com.freshproduce.pos.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** Stock balances, stock movements and authorised stock adjustments (FR-03, FR-08). */
@Service
public class InventoryService {

    private final ProductRepository productRepository;
    private final ConsignmentRepository consignmentRepository;
    private final StockMovementRepository movementRepository;
    private final ConsignmentService consignmentService;

    public InventoryService(ProductRepository productRepository, ConsignmentRepository consignmentRepository,
                            StockMovementRepository movementRepository, ConsignmentService consignmentService) {
        this.productRepository = productRepository;
        this.consignmentRepository = consignmentRepository;
        this.movementRepository = movementRepository;
        this.consignmentService = consignmentService;
    }

    /** Current stock per product, summed over all active consignments. */
    @Transactional(readOnly = true)
    public List<StockSummaryDto> stockSummary() {
        Map<Long, List<Consignment>> active = consignmentRepository.findByStatusOrderByDateReceivedAscIdAsc("ACTIVE")
                .stream().collect(Collectors.groupingBy(c -> c.getProduct().getId()));
        return productRepository.findAllByOrderByNameAsc().stream()
                .map(p -> {
                    List<Consignment> list = active.getOrDefault(p.getId(), List.of());
                    BigDecimal qty = list.stream().map(Consignment::getQuantityRemaining)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    LocalDate oldest = list.stream().map(Consignment::getDateReceived)
                            .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
                    return new StockSummaryDto(p.getId(), p.getName(), p.getCategory(), p.getUnitOfMeasure(),
                            p.getSellingPrice(), qty, list.size(), oldest,
                            qty.multiply(p.getSellingPrice()).setScale(2, RoundingMode.HALF_UP));
                })
                .toList();
    }

    /**
     * Records an adjustment for damaged, unsold or unsuitable produce. The balance is reduced and the
     * adjustment is logged as a stock movement – no sale is created.
     */
    @Transactional
    public MovementDto recordAdjustment(StockAdjustmentRequest request, User user) {
        Consignment consignment = consignmentService.find(request.consignmentId());
        BigDecimal quantity = request.quantity().setScale(2, RoundingMode.HALF_UP);
        if (!"ACTIVE".equals(consignment.getStatus())) {
            throw new BusinessException("Consignment " + consignment.getReferenceNumber() + " has no remaining stock");
        }
        if (quantity.compareTo(consignment.getQuantityRemaining()) > 0) {
            throw new BusinessException(String.format("Adjustment of %s exceeds the remaining balance of %s",
                    quantity.stripTrailingZeros().toPlainString(),
                    consignment.getQuantityRemaining().stripTrailingZeros().toPlainString()));
        }
        consignment.deduct(quantity);

        StockMovement movement = new StockMovement();
        movement.setConsignment(consignment);
        movement.setMovementType(MovementType.ADJUSTMENT);
        movement.setAdjustmentType(request.adjustmentType());
        movement.setQuantity(quantity.negate());
        movement.setBalanceAfter(consignment.getQuantityRemaining());
        movement.setReason(request.reason().trim());
        movement.setReference(consignment.getReferenceNumber());
        movement.setMovementDate(LocalDateTime.now());
        movement.setUser(user);
        return MovementDto.from(movementRepository.save(movement));
    }

    @Transactional(readOnly = true)
    public List<MovementDto> adjustments() {
        return movementRepository.findByMovementTypeOrderByMovementDateDesc(MovementType.ADJUSTMENT)
                .stream().map(MovementDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MovementDto> recentMovements() {
        return movementRepository.findTop100ByOrderByMovementDateDescIdDesc().stream().map(MovementDto::from).toList();
    }
}
