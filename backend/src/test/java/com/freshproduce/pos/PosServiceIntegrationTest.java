package com.freshproduce.pos;

import com.freshproduce.pos.domain.*;
import com.freshproduce.pos.dto.Requests.*;
import com.freshproduce.pos.dto.Responses.*;
import com.freshproduce.pos.exception.InsufficientStockException;
import com.freshproduce.pos.repository.*;
import com.freshproduce.pos.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/** Integration tests against an in-memory H2 database, mapped to the test cases in the documentation. */
@SpringBootTest
class PosServiceIntegrationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CommissionRateRepository rateRepository;
    @Autowired private ConsignmentRepository consignmentRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private FarmerService farmerService;
    @Autowired private ConsignmentService consignmentService;
    @Autowired private SaleService saleService;
    @Autowired private InventoryService inventoryService;
    @SpyBean private CommissionService commissionService;

    private User agent;
    private User admin;
    private FarmerDto farmer;

    @BeforeEach
    void setUp() {
        reset(commissionService);
        int n = COUNTER.incrementAndGet();
        agent = userRepository.save(new User("agent" + n, "x", "Test Agent " + n, Role.AGENT));
        admin = userRepository.save(new User("admin" + n, "x", "Test Admin " + n, Role.ADMINISTRATOR));
        rateRepository.findByActiveTrue().forEach(r -> { r.setActive(false); rateRepository.save(r); });
        rateRepository.save(new CommissionRate(new BigDecimal("5.00"), admin));
        farmer = farmerService.register(new FarmerRequest("Test Farmer " + n, "0820000000", "Test farm"));
    }

    private Product newProduct(String price) {
        return productRepository.save(new Product("Product-" + COUNTER.incrementAndGet(), "Vegetables", "kg",
                new BigDecimal(price)));
    }

    private ConsignmentDto consign(Product product, String qty, int daysAgo) {
        return consignmentService.record(new ConsignmentRequest(farmer.id(), product.getId(), new BigDecimal(qty),
                product.getSellingPrice(), LocalDate.now().minusDays(daysAgo), "A", null), agent);
    }

    private SaleDto sell(Product product, String qty) {
        return saleService.processSale(new SaleRequest(
                List.of(new SaleItemRequest(product.getId(), new BigDecimal(qty))), BigDecimal.ZERO), agent);
    }

    private BigDecimal remaining(ConsignmentDto c) {
        return consignmentRepository.findById(c.id()).orElseThrow().getQuantityRemaining();
    }

    @Test
    void tc01_recordConsignmentAssignsUniqueReference() {
        Product p = newProduct("10.00");
        ConsignmentDto a = consign(p, "100", 0);
        ConsignmentDto b = consign(p, "50", 0);
        assertThat(a.referenceNumber()).startsWith("CON-").isNotEqualTo(b.referenceNumber());
        assertThat(a.quantityRemaining()).isEqualByComparingTo("100");
    }

    @Test
    void tc02_fifoDeductsFromOldestConsignmentFirst() {
        Product p = newProduct("10.00");
        ConsignmentDto older = consign(p, "20", 10);
        ConsignmentDto newer = consign(p, "30", 2);

        SaleDto sale = sell(p, "25");

        assertThat(remaining(older)).isEqualByComparingTo("0");
        assertThat(remaining(newer)).isEqualByComparingTo("25");
        assertThat(consignmentRepository.findById(older.id()).orElseThrow().getStatus()).isEqualTo("DEPLETED");
        List<AllocationDto> allocations = sale.items().get(0).allocations();
        assertThat(allocations).hasSize(2);
        assertThat(allocations.get(0).consignmentReference()).isEqualTo(older.referenceNumber());
        assertThat(allocations.get(0).quantity()).isEqualByComparingTo("20");
        assertThat(allocations.get(1).quantity()).isEqualByComparingTo("5");
    }

    @Test
    void tc03_balanceUpdatesAfterSaleAndHistoryIsRecorded() {
        Product p = newProduct("10.00");
        ConsignmentDto c = consign(p, "100", 1);
        SaleDto sale = sell(p, "30");

        ConsignmentDetailDto detail = consignmentService.getDetail(c.id());
        assertThat(detail.consignment().quantityRemaining()).isEqualByComparingTo("70");
        assertThat(detail.salesHistory()).extracting(SaleHistoryLine::receiptNumber).contains(sale.receiptNumber());
        assertThat(detail.movements()).extracting(MovementDto::movementType)
                .containsExactly(MovementType.RECEIPT, MovementType.SALE);
    }

    @Test
    void tc04_commissionIsCalculatedAtConfiguredRate() {
        Product p = newProduct("10.00");
        consign(p, "200", 1);
        SaleDto sale = sell(p, "100");                 // R1,000
        assertThat(sale.totalAmount()).isEqualByComparingTo("1000.00");
        assertThat(sale.commissionAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void tc10_newCommissionRateAppliesToSubsequentSales() {
        Product p = newProduct("10.00");
        consign(p, "300", 1);
        SaleDto before = sell(p, "100");
        commissionService.updateRate(new BigDecimal("6"), admin);
        SaleDto after = sell(p, "100");
        assertThat(before.commissionAmount()).isEqualByComparingTo("50.00");
        assertThat(after.commissionAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    void tc08_stockAdjustmentReducesBalanceWithoutCreatingSale() {
        Product p = newProduct("10.00");
        ConsignmentDto c = consign(p, "50", 1);
        long salesBefore = saleRepository.count();

        inventoryService.recordAdjustment(new StockAdjustmentRequest(c.id(), new BigDecimal("5"),
                AdjustmentType.DAMAGED, "Bruised"), agent);

        assertThat(remaining(c)).isEqualByComparingTo("45");
        assertThat(saleRepository.count()).isEqualTo(salesBefore);
    }

    @Test
    void tc09_searchConsignmentByReference() {
        Product p = newProduct("10.00");
        ConsignmentDto c = consign(p, "40", 1);
        sell(p, "15");
        ConsignmentDetailDto found = consignmentService.findByReference(c.referenceNumber().toLowerCase());
        assertThat(found.consignment().farmerName()).isEqualTo(farmer.name());
        assertThat(found.consignment().quantityReceived()).isEqualByComparingTo("40");
        assertThat(found.quantitySold()).isEqualByComparingTo("15");
        assertThat(found.consignment().quantityRemaining()).isEqualByComparingTo("25");
    }

    @Test
    void insufficientStockIsRejectedWithoutChanges() {
        Product p = newProduct("10.00");
        ConsignmentDto c = consign(p, "10", 1);
        assertThatThrownBy(() -> sell(p, "11")).isInstanceOf(InsufficientStockException.class);
        assertThat(remaining(c)).isEqualByComparingTo("10");
    }

    @Test
    void rt01_failureDuringSaleRollsBackEverything() {
        Product p = newProduct("10.00");
        ConsignmentDto c = consign(p, "100", 1);
        long salesBefore = saleRepository.count();
        doThrow(new RuntimeException("Simulated database failure")).when(commissionService).recordCommission(any());

        assertThatThrownBy(() -> sell(p, "30")).hasMessageContaining("Simulated database failure");

        assertThat(saleRepository.count()).isEqualTo(salesBefore);
        assertThat(remaining(c)).isEqualByComparingTo("100");
    }
}
