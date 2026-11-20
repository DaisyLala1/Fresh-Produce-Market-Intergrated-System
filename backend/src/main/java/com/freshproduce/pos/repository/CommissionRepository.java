package com.freshproduce.pos.repository;

import com.freshproduce.pos.domain.Commission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CommissionRepository extends JpaRepository<Commission, Long> {

    Optional<Commission> findBySaleId(Long saleId);

    @Query("select c from Commission c join fetch c.sale s where s.saleDate >= :from and s.saleDate < :to " +
           "order by s.saleDate desc")
    List<Commission> findBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(c.commissionAmount), 0) from Commission c join c.sale s " +
           "where c.agent.id = :agentId and s.saleDate >= :from and s.saleDate < :to")
    BigDecimal totalForAgentBetween(@Param("agentId") Long agentId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);
}
