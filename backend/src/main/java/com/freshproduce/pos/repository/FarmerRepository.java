package com.freshproduce.pos.repository;

import com.freshproduce.pos.domain.Farmer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FarmerRepository extends JpaRepository<Farmer, Long> {

    @Query("select f from Farmer f where lower(f.name) like lower(concat('%', :q, '%')) " +
           "or lower(f.farmerCode) like lower(concat('%', :q, '%')) " +
           "or f.contactNumber like concat('%', :q, '%') order by f.name")
    List<Farmer> search(@Param("q") String q);

    List<Farmer> findAllByOrderByNameAsc();
}
