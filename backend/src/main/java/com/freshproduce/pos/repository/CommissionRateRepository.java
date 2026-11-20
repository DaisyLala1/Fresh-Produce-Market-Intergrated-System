package com.freshproduce.pos.repository;

import com.freshproduce.pos.domain.CommissionRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommissionRateRepository extends JpaRepository<CommissionRate, Long> {
    Optional<CommissionRate> findFirstByActiveTrueOrderByEffectiveFromDesc();
    List<CommissionRate> findByActiveTrue();
    List<CommissionRate> findAllByOrderByEffectiveFromDesc();
}
