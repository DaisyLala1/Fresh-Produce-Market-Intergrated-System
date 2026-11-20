package com.freshproduce.pos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Fresh Produce Market POS and Consignment Management System.
 * Entry point of the Spring Boot application (Application Layer).
 */
@SpringBootApplication
public class PosApplication {
    public static void main(String[] args) {
        SpringApplication.run(PosApplication.class, args);
    }
}
