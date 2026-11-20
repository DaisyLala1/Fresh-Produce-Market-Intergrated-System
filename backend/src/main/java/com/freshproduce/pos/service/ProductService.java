package com.freshproduce.pos.service;

import com.freshproduce.pos.domain.Product;
import com.freshproduce.pos.dto.Requests.ProductRequest;
import com.freshproduce.pos.dto.Responses.ProductDto;
import com.freshproduce.pos.exception.BusinessException;
import com.freshproduce.pos.exception.NotFoundException;
import com.freshproduce.pos.repository.ConsignmentRepository;
import com.freshproduce.pos.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Product master data maintained by Administrators (FR-07). */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ConsignmentRepository consignmentRepository;

    public ProductService(ProductRepository productRepository, ConsignmentRepository consignmentRepository) {
        this.productRepository = productRepository;
        this.consignmentRepository = consignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductDto> list(boolean activeOnly) {
        List<Product> products = activeOnly
                ? productRepository.findByStatusOrderByNameAsc("ACTIVE")
                : productRepository.findAllByOrderByNameAsc();
        return products.stream().map(this::toDto).toList();
    }

    @Transactional
    public ProductDto create(ProductRequest request) {
        if (productRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new BusinessException("A product named '" + request.name().trim() + "' already exists");
        }
        Product product = new Product();
        apply(product, request);
        return toDto(productRepository.save(product));
    }

    @Transactional
    public ProductDto update(Long id, ProductRequest request) {
        Product product = find(id);
        if (!product.getName().equalsIgnoreCase(request.name().trim())
                && productRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new BusinessException("A product named '" + request.name().trim() + "' already exists");
        }
        apply(product, request);
        return toDto(product);
    }

    @Transactional
    public ProductDto setStatus(Long id, String status) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new BusinessException("Status must be ACTIVE or INACTIVE");
        }
        Product product = find(id);
        product.setStatus(status);
        return toDto(product);
    }

    public Product find(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product not found"));
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name().trim());
        product.setCategory(request.category().trim());
        product.setUnitOfMeasure(request.unitOfMeasure().trim());
        product.setSellingPrice(request.sellingPrice().setScale(2, RoundingMode.HALF_UP));
    }

    private ProductDto toDto(Product p) {
        return new ProductDto(p.getId(), p.getName(), p.getCategory(), p.getUnitOfMeasure(), p.getSellingPrice(),
                p.getStatus(), consignmentRepository.availableStock(p.getId(), LocalDate.now()));
    }
}
