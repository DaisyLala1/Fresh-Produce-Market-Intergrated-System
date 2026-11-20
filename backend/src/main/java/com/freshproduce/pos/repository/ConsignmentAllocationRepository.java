package com.freshproduce.pos.repository;

import com.freshproduce.pos.domain.ConsignmentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConsignmentAllocationRepository extends JpaRepository<ConsignmentAllocation, Long> {

    @Query("select a from ConsignmentAllocation a join fetch a.saleItem si join fetch si.sale s " +
           "where a.consignment.id = :consignmentId order by s.saleDate asc")
    List<ConsignmentAllocation> findByConsignment(@Param("consignmentId") Long consignmentId);

    @Query("select a from ConsignmentAllocation a join fetch a.saleItem si join fetch si.sale s " +
           "where s.id = :saleId")
    List<ConsignmentAllocation> findBySale(@Param("saleId") Long saleId);
}
