package com.freshproduce.pos.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A farmer who supplies produce to the market on consignment. Stored as a supplier record only. */
@Entity
@Table(name = "farmer")
public class Farmer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "farmer_id")
    private Long id;

    /** Human-friendly code, e.g. FM0001. */
    @Column(name = "farmer_code", unique = true, length = 20)
    private String farmerCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "contact_number", length = 20)
    private String contactNumber;

    @Column(length = 255)
    private String address;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFarmerCode() { return farmerCode; }
    public void setFarmerCode(String farmerCode) { this.farmerCode = farmerCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
