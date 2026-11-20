package com.freshproduce.pos.repository;

import com.freshproduce.pos.domain.Consignment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConsignmentRepository extends JpaRepository<Consignment, Long> {

    Optional<Consignment> findByReferenceNumberIgnoreCase(String referenceNumber);

    /**
     * FIFO: eligible consignments for a product, oldest first. Eligible = active, stock remaining
     * and received on or before the sale date. Rows are locked so concurrent sales cannot
     * allocate the same stock twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Consignment c where c.product.id = :productId and c.status = 'ACTIVE' " +
           "and c.quantityRemaining > 0 and c.dateReceived <= :saleDate " +
           "order by c.dateReceived asc, c.id asc")
    List<Consignment> findEligibleForFifo(@Param("productId") Long productId, @Param("saleDate") LocalDate saleDate);

    @Query("select coalesce(sum(c.quantityRemaining), 0) from Consignment c where c.product.id = :productId " +
           "and c.status = 'ACTIVE' and c.dateReceived <= :asOf")
    BigDecimal availableStock(@Param("productId") Long productId, @Param("asOf") LocalDate asOf);

    List<Consignment> findAllByOrderByDateReceivedDescIdDesc();

    List<Consignment> findByStatusOrderByDateReceivedAscIdAsc(String status);

    List<Consignment> findByDateReceivedBetweenOrderByDateReceivedAsc(LocalDate from, LocalDate to);

    long countByStatus(String status);

    long countByFarmerId(Long farmerId);

    @Query("select coalesce(sum(c.quantityReceived), 0) from Consignment c where c.farmer.id = :farmerId")
    BigDecimal totalSuppliedByFarmer(@Param("farmerId") Long farmerId);
}
