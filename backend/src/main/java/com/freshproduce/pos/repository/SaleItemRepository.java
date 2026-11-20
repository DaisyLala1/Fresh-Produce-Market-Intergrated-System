package com.freshproduce.pos.repository;

import com.freshproduce.pos.domain.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    /** Sale lines for a product since a date – used to build the forecasting history. */
    @Query("select si from SaleItem si join fetch si.sale s where si.product.id = :productId " +
           "and s.saleDate >= :from order by s.saleDate")
    List<SaleItem> findForProductSince(@Param("productId") Long productId, @Param("from") LocalDateTime from);

    @Query("select si from SaleItem si join fetch si.sale s where s.saleDate >= :from and s.saleDate < :to")
    List<SaleItem> findBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
