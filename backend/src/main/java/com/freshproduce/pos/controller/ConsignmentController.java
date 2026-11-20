package com.freshproduce.pos.controller;

import com.freshproduce.pos.dto.Requests.ConsignmentRequest;
import com.freshproduce.pos.dto.Responses.ConsignmentDetailDto;
import com.freshproduce.pos.dto.Responses.ConsignmentDto;
import com.freshproduce.pos.service.ConsignmentService;
import com.freshproduce.pos.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** UC-01 Record Consignment and UC-09 Search Consignment. */
@RestController
@RequestMapping("/api/consignments")
public class ConsignmentController {

    private final ConsignmentService consignmentService;
    private final CurrentUserService currentUserService;

    public ConsignmentController(ConsignmentService consignmentService, CurrentUserService currentUserService) {
        this.consignmentService = consignmentService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<ConsignmentDto> list(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) Long productId,
                                     @RequestParam(required = false) Long farmerId) {
        return consignmentService.list(status, productId, farmerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsignmentDto record(@Valid @RequestBody ConsignmentRequest request) {
        return consignmentService.record(request, currentUserService.get());
    }

    @GetMapping("/{id}")
    public ConsignmentDetailDto detail(@PathVariable Long id) {
        return consignmentService.getDetail(id);
    }

    /** FR-09: GET /api/consignments/search?reference=CON-2026-00001 */
    @GetMapping("/search")
    public ConsignmentDetailDto search(@RequestParam String reference) {
        return consignmentService.findByReference(reference);
    }
}
