package com.freshproduce.pos.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Produce received from a farmer, traceable through its unique reference number (FR-01, FR-09). */
@Entity
@Table(name = "consignment", indexes = {
        @Index(name = "idx_consignment_product_date", columnList = "product_id, received_date")
})
public class Consignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consignment_id")
    private Long id;

    @Column(name = "consignment_reference", unique = true, length = 30)
    private String referenceNumber;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "farmer_id")
    private Farmer farmer;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "received_date", nullable = false)
    private LocalDate dateReceived;

    @Column(name = "quantity_received", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantityReceived;

    @Column(name = "quantity_remaining", nullable = false, precision = 12, scale = 2)
    private BigDecimal quantityRemaining;

    /** Agreed price per unit for this consignment. */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(length = 20)
    private String grade;

    @Column(length = 500)
    private String notes;

    /** ACTIVE while stock remains, DEPLETED when fully sold or adjusted out. */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Reduces the remaining quantity and marks the consignment depleted when nothing is left. */
    public void deduct(BigDecimal quantity) {
        this.quantityRemaining = this.quantityRemaining.subtract(quantity);
        if (this.quantityRemaining.signum() <= 0) {
            this.quantityRemaining = BigDecimal.ZERO;
            this.status = "DEPLETED";
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }
    public Farmer getFarmer() { return farmer; }
    public void setFarmer(Farmer farmer) { this.farmer = farmer; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public LocalDate getDateReceived() { return dateReceived; }
    public void setDateReceived(LocalDate dateReceived) { this.dateReceived = dateReceived; }
    public BigDecimal getQuantityReceived() { return quantityReceived; }
    public void setQuantityReceived(BigDecimal quantityReceived) { this.quantityReceived = quantityReceived; }
    public BigDecimal getQuantityRemaining() { return quantityRemaining; }
    public void setQuantityRemaining(BigDecimal quantityRemaining) { this.quantityRemaining = quantityRemaining; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public User getRecordedBy() { return recordedBy; }
    public void setRecordedBy(User recordedBy) { this.recordedBy = recordedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
