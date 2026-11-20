package com.freshproduce.pos.repository;

import com.freshproduce.pos.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByOrderByNameAsc();
    List<Product> findByStatusOrderByNameAsc(String status);
    boolean existsByNameIgnoreCase(String name);
}
