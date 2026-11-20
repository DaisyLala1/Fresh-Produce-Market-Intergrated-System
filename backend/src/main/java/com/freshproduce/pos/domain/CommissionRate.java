package com.freshproduce.pos.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A configured agent commission rate (FR-10). Only one rate is active at a time; history is kept. */
@Entity
@Table(name = "commission_rate")
public class CommissionRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rate_id")
    private Long id;

    /** Percentage, e.g. 5.00 for 5%. */
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom = LocalDateTime.now();

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "set_by")
    private User setBy;

    public CommissionRate() { }

    public CommissionRate(BigDecimal ratePercent, User setBy) {
        this.ratePercent = ratePercent;
        this.setBy = setBy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public void setRatePercent(BigDecimal ratePercent) { this.ratePercent = ratePercent; }
    public LocalDateTime getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDateTime effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public User getSetBy() { return setBy; }
    public void setSetBy(User setBy) { this.setBy = setBy; }
}
