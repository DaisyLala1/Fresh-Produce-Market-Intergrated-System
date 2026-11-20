package com.freshproduce.pos.dto;

import com.freshproduce.pos.domain.AdjustmentType;
import com.freshproduce.pos.domain.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request bodies accepted by the REST API. Bean Validation enforces the validation rules in section 4.2. */
public final class Requests {

    private Requests() { }

    public record LoginRequest(
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required") String password) { }

    public record FarmerRequest(
            @NotBlank(message = "Farmer name is required") @Size(max = 100) String name,
            @Pattern(regexp = "^$|^[0-9+ ]{7,20}$", message = "Contact number must contain 7 to 20 digits") String contactNumber,
            @Size(max = 255) String address) { }

    public record ProductRequest(
            @NotBlank(message = "Product name is required") @Size(max = 100) String name,
            @NotBlank(message = "Category is required") @Size(max = 50) String category,
            @NotBlank(message = "Unit of measurement is required") @Size(max = 20) String unitOfMeasure,
            @NotNull(message = "Selling price is required")
            @DecimalMin(value = "0.01", message = "Selling price must be greater than zero") BigDecimal sellingPrice) { }

    public record ConsignmentRequest(
            @NotNull(message = "Farmer is required") Long farmerId,
            @NotNull(message = "Product is required") Long productId,
            @NotNull(message = "Quantity is required")
            @DecimalMin(value = "0.01", message = "Quantity must be greater than zero") BigDecimal quantityReceived,
            @NotNull(message = "Unit price is required")
            @DecimalMin(value = "0.01", message = "Unit price must be greater than zero") BigDecimal unitPrice,
            @NotNull(message = "Received date is required")
            @PastOrPresent(message = "Received date cannot be in the future") LocalDate dateReceived,
            @Size(max = 20) String grade,
            @Size(max = 500) String notes) { }

    public record SaleItemRequest(
            @NotNull(message = "Product is required") Long productId,
            @NotNull(message = "Quantity is required")
            @DecimalMin(value = "0.01", message = "Quantity must be greater than zero") BigDecimal quantity) { }

    public record SaleRequest(
            @NotEmpty(message = "A sale must contain at least one item") List<@Valid SaleItemRequest> items,
            @DecimalMin(value = "0.00", message = "Discount cannot be negative") BigDecimal discount) { }

    public record StockAdjustmentRequest(
            @NotNull(message = "Consignment is required") Long consignmentId,
            @NotNull(message = "Quantity is required")
            @DecimalMin(value = "0.01", message = "Adjustment quantity must be greater than zero") BigDecimal quantity,
            @NotNull(message = "Adjustment type is required") AdjustmentType adjustmentType,
            @NotBlank(message = "A reason is required for every stock adjustment") @Size(max = 255) String reason) { }

    public record CommissionRateRequest(
            @NotNull(message = "Rate is required")
            @DecimalMin(value = "0.00", message = "Rate cannot be negative")
            @DecimalMax(value = "100.00", message = "Rate cannot exceed 100%") BigDecimal ratePercent) { }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 6, max = 100, message = "Password must be at least 6 characters") String password,
            @NotBlank @Size(max = 100) String fullName,
            @NotNull Role role) { }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotNull Role role,
            @NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status,
            @Size(min = 6, max = 100, message = "Password must be at least 6 characters") String newPassword) { }

    public record ForecastRequest(
            @NotNull(message = "Product is required") Long productId,
            @NotNull(message = "Forecast period is required")
            @Min(value = 1, message = "Forecast period must be greater than zero")
            @Max(value = 30, message = "Forecast period cannot exceed 30 days") Integer forecastPeriodDays) { }
}
