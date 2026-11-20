package com.freshproduce.pos.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Records which consignment supplied how much of a sale item (FIFO traceability). */
@Entity
@Table(name = "consignment_allocation")
public class ConsignmentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_id")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_item_id")
    private SaleItem saleItem;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "consignment_id")
    private Consignment consignment;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    public ConsignmentAllocation() { }

    public ConsignmentAllocation(Consignment consignment, BigDecimal quantity) {
        this.consignment = consignment;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public SaleItem getSaleItem() { return saleItem; }
    public void setSaleItem(SaleItem saleItem) { this.saleItem = saleItem; }
    public Consignment getConsignment() { return consignment; }
    public void setConsignment(Consignment consignment) { this.consignment = consignment; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
}
