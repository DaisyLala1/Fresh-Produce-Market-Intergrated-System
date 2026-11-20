package com.freshproduce.pos.controller;

import com.freshproduce.pos.service.CurrentUserService;
import com.freshproduce.pos.service.DashboardService;
import com.freshproduce.pos.service.DashboardService.Dashboard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUserService currentUserService;

    public DashboardController(DashboardService dashboardService, CurrentUserService currentUserService) {
        this.dashboardService = dashboardService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public Dashboard dashboard() {
        return dashboardService.build(currentUserService.get());
    }
}
