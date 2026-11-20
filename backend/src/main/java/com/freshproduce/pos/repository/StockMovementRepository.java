package com.freshproduce.pos.repository;

import com.freshproduce.pos.domain.MovementType;
import com.freshproduce.pos.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByConsignmentIdOrderByMovementDateAscIdAsc(Long consignmentId);
    List<StockMovement> findByMovementTypeOrderByMovementDateDesc(MovementType type);
    List<StockMovement> findTop100ByOrderByMovementDateDescIdDesc();
}
