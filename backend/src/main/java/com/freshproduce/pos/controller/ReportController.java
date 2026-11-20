package com.freshproduce.pos.controller;

import com.freshproduce.pos.service.CurrentUserService;
import com.freshproduce.pos.service.ReportService;
import com.freshproduce.pos.service.ReportService.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** UC-04 Generate Reports. Dates default to the last 30 days. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final CurrentUserService currentUserService;

    public ReportController(ReportService reportService, CurrentUserService currentUserService) {
        this.reportService = reportService;
        this.currentUserService = currentUserService;
    }

    private static LocalDate end(LocalDate to) { return to == null ? LocalDate.now() : to; }
    private static LocalDate start(LocalDate from, LocalDate to) { return from == null ? end(to).minusDays(29) : from; }

    @GetMapping("/sales")
    public SalesReport sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.sales(start(from, to), end(to));
    }

    @GetMapping("/consignments")
    public ConsignmentReport consignments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.consignments(start(from, to), end(to));
    }

    @GetMapping("/inventory")
    public InventoryReport inventory() {
        return reportService.inventory();
    }

    @GetMapping("/commissions")
    public CommissionReport commissions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.commissions(start(from, to), end(to), currentUserService.get());
    }
}
