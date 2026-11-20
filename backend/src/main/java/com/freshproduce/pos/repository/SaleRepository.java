package com.freshproduce.pos.repository;

import com.freshproduce.pos.domain.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findBySaleDateBetweenOrderBySaleDateDesc(LocalDateTime from, LocalDateTime to);

    List<Sale> findTop10ByOrderBySaleDateDesc();

    @Query("select coalesce(sum(s.totalAmount), 0) from Sale s where s.saleDate >= :from and s.saleDate < :to")
    BigDecimal totalBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
