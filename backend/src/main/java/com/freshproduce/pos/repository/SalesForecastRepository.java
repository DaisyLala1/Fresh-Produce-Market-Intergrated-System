package com.freshproduce.pos.repository;

import com.freshproduce.pos.domain.SalesForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesForecastRepository extends JpaRepository<SalesForecast, Long> {
    List<SalesForecast> findTop20ByOrderByCreatedDateDesc();
    List<SalesForecast> findTop20ByProductIdOrderByCreatedDateDesc(Long productId);
}
