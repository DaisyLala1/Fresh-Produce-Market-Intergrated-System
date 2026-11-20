package com.freshproduce.pos.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** A fresh produce product sold at the market (FR-07). */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "unit_of_measure", nullable = false, length = 20)
    private String unitOfMeasure;

    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public Product() { }

    public Product(String name, String category, String unitOfMeasure, BigDecimal sellingPrice) {
        this.name = name;
        this.category = category;
        this.unitOfMeasure = unitOfMeasure;
        this.sellingPrice = sellingPrice;
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }
    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) { this.sellingPrice = sellingPrice; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
