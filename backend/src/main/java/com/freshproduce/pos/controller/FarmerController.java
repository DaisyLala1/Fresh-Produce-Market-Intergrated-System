package com.freshproduce.pos.controller;

import com.freshproduce.pos.dto.Requests.FarmerRequest;
import com.freshproduce.pos.dto.Responses.FarmerDto;
import com.freshproduce.pos.service.FarmerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** UC-01 Register Farmer. Agents and Administrators may register; only Administrators change status. */
@RestController
@RequestMapping("/api/farmers")
public class FarmerController {

    private final FarmerService farmerService;

    public FarmerController(FarmerService farmerService) {
        this.farmerService = farmerService;
    }

    @GetMapping
    public List<FarmerDto> list(@RequestParam(required = false) String q) {
        return farmerService.list(q);
    }

    @GetMapping("/{id}")
    public FarmerDto get(@PathVariable Long id) {
        return farmerService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FarmerDto register(@Valid @RequestBody FarmerRequest request) {
        return farmerService.register(request);
    }

    @PutMapping("/{id}")
    public FarmerDto update(@PathVariable Long id, @Valid @RequestBody FarmerRequest request) {
        return farmerService.update(id, request);
    }

    /** Administrator only (enforced in SecurityConfig). Body: {"status":"INACTIVE"} */
    @PatchMapping("/{id}/status")
    public FarmerDto setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return farmerService.setStatus(id, body.get("status"));
    }
}
