package com.freshproduce.pos.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Every change to consignment stock: receipts (+), sales (-) and authorised adjustments (-).
 * Adjustments also carry an adjustment type and a reason (FR-08).
 */
@Entity
@Table(name = "stock_movement", indexes = @Index(name = "idx_movement_consignment", columnList = "consignment_id"))
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movement_id")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "consignment_id")
    private Consignment consignment;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 20)
    private MovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", length = 20)
    private AdjustmentType adjustmentType;

    /** Signed quantity: positive for receipts, negative for sales and adjustments. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "balance_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 255)
    private String reason;

    /** Receipt number of the sale, or consignment reference for receipts. */
    @Column(length = 50)
    private String reference;

    @Column(name = "movement_date", nullable = false)
    private LocalDateTime movementDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Consignment getConsignment() { return consignment; }
    public void setConsignment(Consignment consignment) { this.consignment = consignment; }
    public MovementType getMovementType() { return movementType; }
    public void setMovementType(MovementType movementType) { this.movementType = movementType; }
    public AdjustmentType getAdjustmentType() { return adjustmentType; }
    public void setAdjustmentType(AdjustmentType adjustmentType) { this.adjustmentType = adjustmentType; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public LocalDateTime getMovementDate() { return movementDate; }
    public void setMovementDate(LocalDateTime movementDate) { this.movementDate = movementDate; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
