package com.freshproduce.pos.controller;

import com.freshproduce.pos.dto.Requests.ProductRequest;
import com.freshproduce.pos.dto.Responses.ProductDto;
import com.freshproduce.pos.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** UC-07 Manage Products. Reading is open to all users; changes are Administrator-only (SecurityConfig). */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductDto> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return productService.list(activeOnly);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductDto update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ProductDto setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return productService.setStatus(id, body.get("status"));
    }
}
