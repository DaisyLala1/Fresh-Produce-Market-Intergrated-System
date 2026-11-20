package com.freshproduce.pos.controller;

import com.freshproduce.pos.dto.Requests.CommissionRateRequest;
import com.freshproduce.pos.dto.Responses.CommissionRateDto;
import com.freshproduce.pos.service.CommissionService;
import com.freshproduce.pos.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** UC-10 Configure Commission Rates. Updating is Administrator-only (SecurityConfig). */
@RestController
@RequestMapping("/api/commission-rates")
public class CommissionController {

    private final CommissionService commissionService;
    private final CurrentUserService currentUserService;

    public CommissionController(CommissionService commissionService, CurrentUserService currentUserService) {
        this.commissionService = commissionService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/current")
    public CommissionRateDto current() {
        return CommissionRateDto.from(commissionService.currentRate());
    }

    @GetMapping
    public List<CommissionRateDto> history() {
        return commissionService.history();
    }

    @PutMapping
    public CommissionRateDto update(@Valid @RequestBody CommissionRateRequest request) {
        return commissionService.updateRate(request.ratePercent(), currentUserService.get());
    }
}
