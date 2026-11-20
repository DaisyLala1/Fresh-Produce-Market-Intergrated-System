package com.freshproduce.pos.controller;

import com.freshproduce.pos.dto.Requests.ForecastRequest;
import com.freshproduce.pos.dto.Responses.ForecastDto;
import com.freshproduce.pos.service.CurrentUserService;
import com.freshproduce.pos.service.ForecastService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** UC-05 View Sales Forecast (includes UC Generate Sales Forecast on the AI service). */
@RestController
@RequestMapping("/api/forecast")
public class ForecastController {

    private final ForecastService forecastService;
    private final CurrentUserService currentUserService;

    public ForecastController(ForecastService forecastService, CurrentUserService currentUserService) {
        this.forecastService = forecastService;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ForecastDto generate(@Valid @RequestBody ForecastRequest request) {
        return forecastService.generate(request, currentUserService.get());
    }

    @GetMapping("/history")
    public List<ForecastDto> history(@RequestParam(required = false) Long productId) {
        return forecastService.history(productId);
    }
}
