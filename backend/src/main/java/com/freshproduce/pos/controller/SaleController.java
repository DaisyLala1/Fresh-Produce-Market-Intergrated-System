package com.freshproduce.pos.controller;

import com.freshproduce.pos.dto.Requests.SaleRequest;
import com.freshproduce.pos.dto.Responses.SaleDto;
import com.freshproduce.pos.dto.Responses.SaleSummaryDto;
import com.freshproduce.pos.service.CurrentUserService;
import com.freshproduce.pos.service.SaleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** UC-02 Process Sale (POS) with FIFO allocation. */
@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;
    private final CurrentUserService currentUserService;

    public SaleController(SaleService saleService, CurrentUserService currentUserService) {
        this.saleService = saleService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleDto processSale(@Valid @RequestBody SaleRequest request) {
        return saleService.processSale(request, currentUserService.get());
    }

    @GetMapping
    public List<SaleSummaryDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(6) : from;
        return saleService.list(start, end, currentUserService.get());
    }

    @GetMapping("/{id}")
    public SaleDto get(@PathVariable Long id) {
        return saleService.get(id);
    }
}
