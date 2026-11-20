package com.freshproduce.pos.service;

import com.freshproduce.pos.domain.User;
import com.freshproduce.pos.dto.Responses.SaleSummaryDto;
import com.freshproduce.pos.repository.CommissionRepository;
import com.freshproduce.pos.repository.ConsignmentRepository;
import com.freshproduce.pos.repository.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Figures shown on the dashboard. */
@Service
public class DashboardService {

    private final SaleRepository saleRepository;
    private final ConsignmentRepository consignmentRepository;
    private final CommissionRepository commissionRepository;
    private final InventoryService inventoryService;
    private final SaleService saleService;

    public DashboardService(SaleRepository saleRepository, ConsignmentRepository consignmentRepository,
                            CommissionRepository commissionRepository, InventoryService inventoryService,
                            SaleService saleService) {
        this.saleRepository = saleRepository;
        this.consignmentRepository = consignmentRepository;
        this.commissionRepository = commissionRepository;
        this.inventoryService = inventoryService;
        this.saleService = saleService;
    }

    public record DayTotal(LocalDate date, BigDecimal total) { }

    public record Dashboard(BigDecimal salesToday, BigDecimal salesYesterday, BigDecimal changePercent,
                            long activeConsignments, long productsInStock, BigDecimal myCommissionToday,
                            List<SaleSummaryDto> recentSales, List<DayTotal> lastSevenDays) { }

    @Transactional(readOnly = true)
    public Dashboard build(User user) {
        LocalDate today = LocalDate.now();
        BigDecimal todayTotal = saleRepository.totalBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        BigDecimal yesterday = saleRepository.totalBetween(today.minusDays(1).atStartOfDay(), today.atStartOfDay());
        BigDecimal change = yesterday.signum() == 0 ? null
                : todayTotal.subtract(yesterday).multiply(BigDecimal.valueOf(100))
                    .divide(yesterday, 1, RoundingMode.HALF_UP);
        List<DayTotal> week = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            week.add(new DayTotal(d, saleRepository.totalBetween(d.atStartOfDay(), d.plusDays(1).atStartOfDay())));
        }
        long inStock = inventoryService.stockSummary().stream().filter(s -> s.quantityAvailable().signum() > 0).count();
        BigDecimal myCommission = commissionRepository.totalForAgentBetween(user.getId(), today.atStartOfDay(),
                today.plusDays(1).atStartOfDay());
        return new Dashboard(todayTotal, yesterday, change, consignmentRepository.countByStatus("ACTIVE"), inStock,
                myCommission, saleService.recent(), week);
    }
}
