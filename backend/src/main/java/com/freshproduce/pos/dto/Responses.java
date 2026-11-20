package com.freshproduce.pos.dto;

import com.freshproduce.pos.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Response bodies returned by the REST API. Entities are never exposed directly. */
public final class Responses {

    private Responses() { }

    public record UserDto(Long id, String username, String fullName, Role role, String status) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.getStatus());
        }
    }

    public record FarmerDto(Long id, String farmerCode, String name, String contactNumber, String address,
                            String status, long totalConsignments, BigDecimal totalQuantitySupplied) { }

    public record ProductDto(Long id, String name, String category, String unitOfMeasure,
                             BigDecimal sellingPrice, String status, BigDecimal quantityAvailable) { }

    public record ConsignmentDto(Long id, String referenceNumber, Long farmerId, String farmerCode, String farmerName,
                                 Long productId, String productName, String unitOfMeasure, LocalDate dateReceived,
                                 BigDecimal quantityReceived, BigDecimal quantityRemaining, BigDecimal unitPrice,
                                 String grade, String notes, String status, String recordedBy) {
        public static ConsignmentDto from(Consignment c) {
            return new ConsignmentDto(c.getId(), c.getReferenceNumber(), c.getFarmer().getId(),
                    c.getFarmer().getFarmerCode(), c.getFarmer().getName(), c.getProduct().getId(),
                    c.getProduct().getName(), c.getProduct().getUnitOfMeasure(), c.getDateReceived(),
                    c.getQuantityReceived(), c.getQuantityRemaining(), c.getUnitPrice(), c.getGrade(),
                    c.getNotes(), c.getStatus(),
                    c.getRecordedBy() == null ? null : c.getRecordedBy().getFullName());
        }
    }

    public record SaleHistoryLine(String receiptNumber, LocalDateTime saleDate, String agentName,
                                  BigDecimal quantity, BigDecimal unitPrice, BigDecimal value) { }

    public record MovementDto(Long id, String consignmentReference, String productName, String unitOfMeasure,
                              MovementType movementType, AdjustmentType adjustmentType, BigDecimal quantity,
                              BigDecimal balanceAfter, String reason, String reference,
                              LocalDateTime movementDate, String userName) {
        public static MovementDto from(StockMovement m) {
            return new MovementDto(m.getId(), m.getConsignment().getReferenceNumber(),
                    m.getConsignment().getProduct().getName(), m.getConsignment().getProduct().getUnitOfMeasure(),
                    m.getMovementType(), m.getAdjustmentType(), m.getQuantity(), m.getBalanceAfter(),
                    m.getReason(), m.getReference(), m.getMovementDate(),
                    m.getUser() == null ? null : m.getUser().getFullName());
        }
    }

    public record ConsignmentDetailDto(ConsignmentDto consignment, String farmerContact, String farmerAddress,
                                       BigDecimal quantitySold, BigDecimal quantityAdjusted, BigDecimal salesValue,
                                       List<SaleHistoryLine> salesHistory, List<MovementDto> movements) { }

    public record AllocationDto(Long consignmentId, String consignmentReference, String farmerName,
                                LocalDate dateReceived, BigDecimal quantity) { }

    public record SaleItemDto(Long productId, String productName, String unitOfMeasure, BigDecimal quantity,
                              BigDecimal unitPrice, BigDecimal subtotal, List<AllocationDto> allocations) { }

    public record SaleDto(Long id, String receiptNumber, LocalDateTime saleDate, String agentName,
                          BigDecimal subTotal, BigDecimal discount, BigDecimal totalAmount,
                          BigDecimal commissionRate, BigDecimal commissionAmount, List<SaleItemDto> items) { }

    public record SaleSummaryDto(Long id, String receiptNumber, LocalDateTime saleDate, String agentName,
                                 int itemCount, String productsSummary, BigDecimal totalAmount,
                                 BigDecimal commissionAmount) { }

    public record StockSummaryDto(Long productId, String productName, String category, String unitOfMeasure,
                                  BigDecimal sellingPrice, BigDecimal quantityAvailable, long activeConsignments,
                                  LocalDate oldestReceived, BigDecimal stockValue) { }

    public record CommissionRateDto(Long id, BigDecimal ratePercent, LocalDateTime effectiveFrom, boolean active,
                                    String setBy) {
        public static CommissionRateDto from(CommissionRate r) {
            return new CommissionRateDto(r.getId(), r.getRatePercent(), r.getEffectiveFrom(), r.isActive(),
                    r.getSetBy() == null ? null : r.getSetBy().getFullName());
        }
    }

    public record DailyPoint(LocalDate date, BigDecimal quantity) { }

    public record ForecastDto(Long id, Long productId, String productName, String unitOfMeasure,
                              BigDecimal sellingPrice, LocalDate periodStart, LocalDate periodEnd,
                              int forecastPeriodDays, BigDecimal predictedQuantity, BigDecimal expectedRevenue,
                              String modelType, BigDecimal modelMae, BigDecimal baselineMae,
                              BigDecimal accuracyPercent, List<DailyPoint> dailyForecast,
                              List<DailyPoint> recentHistory, LocalDateTime createdDate, String requestedBy) { }

    public record ErrorResponse(int status, String error, String message, List<String> details,
                                LocalDateTime timestamp) { }
}
