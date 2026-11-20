package com.freshproduce.pos.service;

import com.freshproduce.pos.domain.Farmer;
import com.freshproduce.pos.dto.Requests.FarmerRequest;
import com.freshproduce.pos.dto.Responses.FarmerDto;
import com.freshproduce.pos.exception.BusinessException;
import com.freshproduce.pos.exception.NotFoundException;
import com.freshproduce.pos.repository.ConsignmentRepository;
import com.freshproduce.pos.repository.FarmerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** Farmer (supplier) registration and maintenance – part of FR-01. */
@Service
public class FarmerService {

    private final FarmerRepository farmerRepository;
    private final ConsignmentRepository consignmentRepository;

    public FarmerService(FarmerRepository farmerRepository, ConsignmentRepository consignmentRepository) {
        this.farmerRepository = farmerRepository;
        this.consignmentRepository = consignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<FarmerDto> list(String query) {
        List<Farmer> farmers = StringUtils.hasText(query)
                ? farmerRepository.search(query.trim())
                : farmerRepository.findAllByOrderByNameAsc();
        return farmers.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public FarmerDto get(Long id) {
        return toDto(find(id));
    }

    @Transactional
    public FarmerDto register(FarmerRequest request) {
        Farmer farmer = new Farmer();
        apply(farmer, request);
        farmer = farmerRepository.save(farmer);
        farmer.setFarmerCode(CodeGenerator.farmerCode(farmer.getId()));
        return toDto(farmer);
    }

    @Transactional
    public FarmerDto update(Long id, FarmerRequest request) {
        Farmer farmer = find(id);
        apply(farmer, request);
        return toDto(farmer);
    }

    @Transactional
    public FarmerDto setStatus(Long id, String status) {
        if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) {
            throw new BusinessException("Status must be ACTIVE or INACTIVE");
        }
        Farmer farmer = find(id);
        farmer.setStatus(status);
        return toDto(farmer);
    }

    public Farmer find(Long id) {
        return farmerRepository.findById(id).orElseThrow(() -> new NotFoundException("Farmer not found"));
    }

    private void apply(Farmer farmer, FarmerRequest request) {
        farmer.setName(request.name().trim());
        farmer.setContactNumber(request.contactNumber());
        farmer.setAddress(request.address());
    }

    private FarmerDto toDto(Farmer f) {
        return new FarmerDto(f.getId(), f.getFarmerCode(), f.getName(), f.getContactNumber(), f.getAddress(),
                f.getStatus(), consignmentRepository.countByFarmerId(f.getId()),
                consignmentRepository.totalSuppliedByFarmer(f.getId()));
    }
}
