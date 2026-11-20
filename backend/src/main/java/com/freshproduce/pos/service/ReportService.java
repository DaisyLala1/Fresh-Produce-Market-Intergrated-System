package com.freshproduce.pos.service;

import com.freshproduce.pos.domain.*;
import com.freshproduce.pos.dto.Responses.SaleSummaryDto;
import com.freshproduce.pos.dto.Responses.StockSummaryDto;
import com.freshproduce.pos.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Sales, consignment, inventory and commission reports (FR-04). */
@Service
public class ReportService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ConsignmentRepository consignmentRepository;
    private final ConsignmentAllocationRepository allocationRepository;
    private final StockMovementRepository movementRepository;
    private final CommissionRepository commissionRepository;
    private final InventoryService inventoryService;
    private final SaleService saleService;

    public ReportService(SaleRepository saleRepository, SaleItemRepository saleItemRepository,
                         ConsignmentRepository consignmentRepository,
                         ConsignmentAllocationRepository allocationRepository,
                         StockMovementRepository movementRepository, CommissionRepository commissionRepository,
                         InventoryService inventoryService, SaleService saleService) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.consignmentRepository = consignmentRepository;
        this.allocationRepository = allocationRepository;
        this.movementRepository = movementRepository;
        this.commissionRepository = commissionRepository;
        this.inventoryService = inventoryService;
        this.saleService = saleService;
    }

    public record ProductSalesLine(String productName, String unitOfMeasure, BigDecimal quantity, BigDecimal revenue) { }
    public record DaySalesLine(LocalDate date, long transactions, BigDecimal revenue) { }
    public record SalesReport(LocalDate from, LocalDate to, long transactions, BigDecimal totalRevenue,
                              BigDecimal totalDiscount, BigDecimal totalCommission,
                              List<ProductSalesLine> byProduct, List<DaySalesLine> byDay,
                              List<SaleSummaryDto> sales) { }

    public record ConsignmentLine(String referenceNumber, String farmerName, String productName, String unitOfMeasure,
                                  LocalDate dateReceived, BigDecimal quantityReceived, BigDecimal quantitySold,
                                  BigDecimal quantityAdjusted, BigDecimal quantityRemaining, BigDecimal salesValue,
                                  String status) { }
    public record FarmerLine(String farmerName, long consignments, BigDecimal quantityReceived,
                             BigDecimal quantitySold, BigDecimal salesValue) { }
    public record ConsignmentReport(LocalDate from, LocalDate to, List<ConsignmentLine> consignments,
                                    List<FarmerLine> byFarmer) { }

    public record AgedConsignment(String referenceNumber, String productName, String farmerName,
                                  LocalDate dateReceived, long ageDays, BigDecimal quantityRemaining,
                                  String unitOfMeasure) { }
    public record InventoryReport(LocalDate asOf, BigDecimal totalStockValue, List<StockSummaryDto> products,
                                  List<AgedConsignment> activeConsignments) { }

    public record AgentCommissionLine(String agentName, long sales, BigDecimal salesValue, BigDecimal commission) { }
    public record CommissionReport(LocalDate from, LocalDate to, BigDecimal totalCommission,
                                   List<AgentCommissionLine> byAgent) { }

    private static LocalDateTime start(LocalDate d) { return d.atStartOfDay(); }
    private static LocalDateTime endExclusive(LocalDate d) { return d.plusDays(1).atStartOfDay(); }

    @Transactional(readOnly = true)
    public SalesReport sales(LocalDate from, LocalDate to) {
        List<Sale> sales = saleRepository.findBySaleDateBetweenOrderBySaleDateDesc(start(from),
                endExclusive(to).minusNanos(1));
        Map<String, ProductSalesLine> byProduct = new TreeMap<>();
        for (SaleItem item : saleItemRepository.findBetween(start(from), endExclusive(to))) {
            String name = item.getProduct().getName();
            ProductSalesLine prev = byProduct.get(name);
            BigDecimal q = item.getQuantity().add(prev == null ? BigDecimal.ZERO : prev.quantity());
            BigDecimal r = item.getSubtotal().add(prev == null ? BigDecimal.ZERO : prev.revenue());
            byProduct.put(name, new ProductSalesLine(name, item.getProduct().getUnitOfMeasure(), q, r));
        }
        Map<LocalDate, long[]> dayCounts = new TreeMap<>();
        Map<LocalDate, BigDecimal> dayRevenue = new TreeMap<>();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        for (Sale s : sales) {
            LocalDate d = s.getSaleDate().toLocalDate();
            dayCounts.computeIfAbsent(d, k -> new long[1])[0]++;
            dayRevenue.merge(d, s.getTotalAmount(), BigDecimal::add);
            revenue = revenue.add(s.getTotalAmount());
            discount = discount.add(s.getDiscount());
        }
        List<DaySalesLine> byDay = dayCounts.keySet().stream()
                .map(d -> new DaySalesLine(d, dayCounts.get(d)[0], dayRevenue.get(d))).toList();
        BigDecimal commission = commissionRepository.findBetween(start(from), endExclusive(to)).stream()
                .map(Commission::getCommissionAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ProductSalesLine> products = new ArrayList<>(byProduct.values());
        products.sort(Comparator.comparing(ProductSalesLine::revenue).reversed());
        return new SalesReport(from, to, sales.size(), revenue, discount, commission, products, byDay,
                sales.stream().map(saleService::toSummary).toList());
    }

    @Transactional(readOnly = true)
    public ConsignmentReport consignments(LocalDate from, LocalDate to) {
        List<ConsignmentLine> lines = new ArrayList<>();
        Map<String, FarmerLine> byFarmer = new TreeMap<>();
        for (Consignment c : consignmentRepository.findByDateReceivedBetweenOrderByDateReceivedAsc(from, to)) {
            BigDecimal sold = BigDecimal.ZERO;
            BigDecimal value = BigDecimal.ZERO;
            for (ConsignmentAllocation a : allocationRepository.findByConsignment(c.getId())) {
                sold = sold.add(a.getQuantity());
                value = value.add(a.getQuantity().multiply(a.getSaleItem().getUnitPrice()));
            }
            value = value.setScale(2, RoundingMode.HALF_UP);
            BigDecimal adjusted = movementRepository.findByConsignmentIdOrderByMovementDateAscIdAsc(c.getId()).stream()
                    .filter(m -> m.getMovementType() == MovementType.ADJUSTMENT)
                    .map(m -> m.getQuantity().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);
            lines.add(new ConsignmentLine(c.getReferenceNumber(), c.getFarmer().getName(), c.getProduct().getName(),
                    c.getProduct().getUnitOfMeasure(), c.getDateReceived(), c.getQuantityReceived(), sold, adjusted,
                    c.getQuantityRemaining(), value, c.getStatus()));

            FarmerLine prev = byFarmer.get(c.getFarmer().getName());
            byFarmer.put(c.getFarmer().getName(), new FarmerLine(c.getFarmer().getName(),
                    (prev == null ? 0 : prev.consignments()) + 1,
                    c.getQuantityReceived().add(prev == null ? BigDecimal.ZERO : prev.quantityReceived()),
                    sold.add(prev == null ? BigDecimal.ZERO : prev.quantitySold()),
                    value.add(prev == null ? BigDecimal.ZERO : prev.salesValue())));
        }
        return new ConsignmentReport(from, to, lines, new ArrayList<>(byFarmer.values()));
    }

    @Transactional(readOnly = true)
    public InventoryReport inventory() {
        List<StockSummaryDto> products = inventoryService.stockSummary();
        BigDecimal total = products.stream().map(StockSummaryDto::stockValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate today = LocalDate.now();
        List<AgedConsignment> aged = consignmentRepository.findByStatusOrderByDateReceivedAscIdAsc("ACTIVE").stream()
                .map(c -> new AgedConsignment(c.getReferenceNumber(), c.getProduct().getName(), c.getFarmer().getName(),
                        c.getDateReceived(), ChronoUnit.DAYS.between(c.getDateReceived(), today),
                        c.getQuantityRemaining(), c.getProduct().getUnitOfMeasure()))
                .toList();
        return new InventoryReport(today, total, products, aged);
    }

    /** Administrators see every agent; an Agent only sees their own commission. */
    @Transactional(readOnly = true)
    public CommissionReport commissions(LocalDate from, LocalDate to, User viewer) {
        boolean admin = viewer.getRole() == Role.ADMINISTRATOR;
        Map<String, AgentCommissionLine> byAgent = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Commission c : commissionRepository.findBetween(start(from), endExclusive(to))) {
            if (!admin && !c.getAgent().getId().equals(viewer.getId())) {
                continue;
            }
            String name = c.getAgent().getFullName();
            AgentCommissionLine prev = byAgent.get(name);
            byAgent.put(name, new AgentCommissionLine(name, (prev == null ? 0 : prev.sales()) + 1,
                    c.getSale().getTotalAmount().add(prev == null ? BigDecimal.ZERO : prev.salesValue()),
                    c.getCommissionAmount().add(prev == null ? BigDecimal.ZERO : prev.commission())));
            total = total.add(c.getCommissionAmount());
        }
        return new CommissionReport(from, to, total, new ArrayList<>(byAgent.values()));
    }
}
