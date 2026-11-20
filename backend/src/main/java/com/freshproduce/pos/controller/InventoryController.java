package com.freshproduce.pos.controller;

import com.freshproduce.pos.dto.Requests.StockAdjustmentRequest;
import com.freshproduce.pos.dto.Responses.MovementDto;
import com.freshproduce.pos.dto.Responses.StockSummaryDto;
import com.freshproduce.pos.service.CurrentUserService;
import com.freshproduce.pos.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** UC-03 View Inventory and UC-08 Record Stock Adjustment. */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final CurrentUserService currentUserService;

    public InventoryController(InventoryService inventoryService, CurrentUserService currentUserService) {
        this.inventoryService = inventoryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<StockSummaryDto> stock() {
        return inventoryService.stockSummary();
    }

    @GetMapping("/movements")
    public List<MovementDto> movements() {
        return inventoryService.recentMovements();
    }

    @GetMapping("/adjustments")
    public List<MovementDto> adjustments() {
        return inventoryService.adjustments();
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementDto adjust(@Valid @RequestBody StockAdjustmentRequest request) {
        return inventoryService.recordAdjustment(request, currentUserService.get());
    }
}
