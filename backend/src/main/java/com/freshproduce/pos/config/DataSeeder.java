package com.freshproduce.pos.config;

import com.freshproduce.pos.domain.*;
import com.freshproduce.pos.dto.Requests.*;
import com.freshproduce.pos.dto.Responses.ConsignmentDto;
import com.freshproduce.pos.dto.Responses.FarmerDto;
import com.freshproduce.pos.repository.CommissionRateRepository;
import com.freshproduce.pos.repository.ProductRepository;
import com.freshproduce.pos.repository.UserRepository;
import com.freshproduce.pos.service.ConsignmentService;
import com.freshproduce.pos.service.FarmerService;
import com.freshproduce.pos.service.InventoryService;
import com.freshproduce.pos.service.SaleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Loads demonstration data the first time the application starts on an empty database:
 * users, products, farmers, consignments and ~90 days of sales history so FIFO, reports and the
 * AI forecasting component can be demonstrated straight away. Disable with app.seed.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final int HISTORY_DAYS = 90;
    private static final int SALES_PER_DAY = 3;
    /** Monday..Sunday demand multipliers – busier towards the weekend. */
    private static final double[] WEEKDAY_FACTOR = {0.85, 0.90, 0.95, 1.00, 1.20, 1.35, 0.75};

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CommissionRateRepository rateRepository;
    private final PasswordEncoder passwordEncoder;
    private final FarmerService farmerService;
    private final ConsignmentService consignmentService;
    private final SaleService saleService;
    private final InventoryService inventoryService;

    public DataSeeder(UserRepository userRepository, ProductRepository productRepository,
                      CommissionRateRepository rateRepository, PasswordEncoder passwordEncoder,
                      FarmerService farmerService, ConsignmentService consignmentService,
                      SaleService saleService, InventoryService inventoryService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.rateRepository = rateRepository;
        this.passwordEncoder = passwordEncoder;
        this.farmerService = farmerService;
        this.consignmentService = consignmentService;
        this.saleService = saleService;
        this.inventoryService = inventoryService;
    }

    private record SeedProduct(String name, String category, String unit, String price, int dailyDemand) { }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        log.info("Empty database detected - loading demonstration data (this takes a few seconds)...");
        Random random = new Random(42);

        User admin = userRepository.save(new User("admin", passwordEncoder.encode("Admin@123"),
                "System Administrator", Role.ADMINISTRATOR));
        User agent1 = userRepository.save(new User("agent1", passwordEncoder.encode("Agent@123"),
                "Thabo Mokoena", Role.AGENT));
        User agent2 = userRepository.save(new User("agent2", passwordEncoder.encode("Agent@123"),
                "Lerato Dlamini", Role.AGENT));
        List<User> agents = List.of(agent1, agent2);

        rateRepository.save(new CommissionRate(new BigDecimal("5.00"), admin));

        List<SeedProduct> seeds = List.of(
                new SeedProduct("Tomatoes", "Vegetables", "kg", "15.00", 60),
                new SeedProduct("Potatoes", "Vegetables", "kg", "10.00", 80),
                new SeedProduct("Onions", "Vegetables", "kg", "12.00", 50),
                new SeedProduct("Carrots", "Vegetables", "kg", "10.00", 35),
                new SeedProduct("Green Peppers", "Vegetables", "kg", "18.00", 20),
                new SeedProduct("Cabbage", "Vegetables", "kg", "8.00", 30),
                new SeedProduct("Spinach", "Vegetables", "bunch", "14.00", 25),
                new SeedProduct("Bananas", "Fruit", "kg", "12.00", 45),
                new SeedProduct("Apples", "Fruit", "kg", "16.00", 40),
                new SeedProduct("Oranges", "Fruit", "kg", "14.00", 35),
                new SeedProduct("Avocados", "Fruit", "kg", "22.00", 15),
                new SeedProduct("Plantains", "Fruit", "kg", "11.00", 10));
        List<Product> products = new ArrayList<>();
        for (SeedProduct s : seeds) {
            products.add(productRepository.save(new Product(s.name(), s.category(), s.unit(), new BigDecimal(s.price()))));
        }

        List<FarmerDto> farmers = List.of(
                farmerService.register(new FarmerRequest("John Dlamini", "0821234567", "Plot 12, Pretoria North")),
                farmerService.register(new FarmerRequest("Sipho Ndlovu", "0732345678", "Farm 45, Polokwane")),
                farmerService.register(new FarmerRequest("Maria van der Merwe", "0613456789", "Rietfontein Farm, Brits")),
                farmerService.register(new FarmerRequest("Thandi Khumalo", "0794567890", "Mbombela Road, Nelspruit")),
                farmerService.register(new FarmerRequest("Peter Mahlangu", "0835678901", "Bronkhorstspruit Smallholding")));

        // Daily demand plan: weekly seasonality + monthly cycle + gentle trend + noise.
        LocalDate today = LocalDate.now();
        int[][] plan = new int[HISTORY_DAYS + 1][products.size()];
        for (int ago = HISTORY_DAYS; ago >= 1; ago--) {
            LocalDate day = today.minusDays(ago);
            double weekday = WEEKDAY_FACTOR[day.getDayOfWeek().getValue() - 1];
            double cycle = 1 + 0.10 * Math.sin(2 * Math.PI * day.getDayOfMonth() / 30.0);
            double trend = 1 + 0.002 * (HISTORY_DAYS - ago);
            for (int p = 0; p < products.size(); p++) {
                double noise = 1 + random.nextGaussian() * 0.12;
                plan[ago][p] = (int) Math.max(0, Math.round(seeds.get(p).dailyDemand() * weekday * cycle * trend * noise));
            }
        }

        // Two older consignments per product (FIFO moves from the first to the second) and one fresh one.
        for (int p = 0; p < products.size(); p++) {
            int firstHalf = 0;
            int secondHalf = 0;
            for (int ago = HISTORY_DAYS; ago >= 1; ago--) {
                if (ago > HISTORY_DAYS / 2) firstHalf += plan[ago][p]; else secondHalf += plan[ago][p];
            }
            Product product = products.get(p);
            recordConsignment(farmers.get(p % farmers.size()), product, Math.round(firstHalf * 1.02f), today.minusDays(HISTORY_DAYS + 1), agent1);
            recordConsignment(farmers.get((p + 1) % farmers.size()), product, Math.round(secondHalf * 1.06f), today.minusDays(HISTORY_DAYS / 2 + 1), agent2);
            recordConsignment(farmers.get((p + 2) % farmers.size()), product, seeds.get(p).dailyDemand() * 4, today.minusDays(1), agent1);
        }

        // Historical sales processed through the real POS service (FIFO + commission).
        for (int ago = HISTORY_DAYS; ago >= 1; ago--) {
            LocalDate day = today.minusDays(ago);
            int[][] split = new int[SALES_PER_DAY][products.size()];
            for (int p = 0; p < products.size(); p++) {
                int remaining = plan[ago][p];
                for (int s = 0; s < SALES_PER_DAY - 1; s++) {
                    int part = remaining == 0 ? 0 : random.nextInt(remaining + 1) / 2;
                    split[s][p] = part;
                    remaining -= part;
                }
                split[SALES_PER_DAY - 1][p] = remaining;
            }
            for (int s = 0; s < SALES_PER_DAY; s++) {
                LocalDateTime when = day.atTime(LocalTime.of(7 + s * 3, random.nextInt(60)));
                sell(split[s], products, agents.get((ago + s) % agents.size()), when);
            }
        }

        // A couple of sales today so the dashboard has live figures.
        LocalDateTime now = LocalDateTime.now();
        for (int s = 0; s < 2; s++) {
            int[] qty = new int[products.size()];
            for (int p = 0; p < products.size(); p++) {
                qty[p] = random.nextInt(3) == 0 ? 0 : 1 + random.nextInt(Math.max(1, seeds.get(p).dailyDemand() / 6));
            }
            LocalDateTime when = now.minusMinutes(30L * (2 - s) + 1);
            if (!when.toLocalDate().equals(today)) {
                when = today.atStartOfDay().plusMinutes(1 + s);
            }
            sell(qty, products, agents.get(s), when);
        }

        // One example stock adjustment (FR-08).
        consignmentService.list("ACTIVE", products.get(0).getId(), null).stream().findFirst().ifPresent(c ->
                inventoryService.recordAdjustment(new StockAdjustmentRequest(c.id(), new BigDecimal("5"),
                        AdjustmentType.DAMAGED, "Bruised during offloading"), agent1));

        log.info("Demo data loaded. Log in as admin / Admin@123 or agent1 / Agent@123");
    }

    private ConsignmentDto recordConsignment(FarmerDto farmer, Product product, int quantity, LocalDate received, User by) {
        return consignmentService.record(new ConsignmentRequest(farmer.id(), product.getId(),
                BigDecimal.valueOf(Math.max(quantity, 1)), product.getSellingPrice(), received, "Grade A",
                "Seed data"), by);
    }

    private void sell(int[] quantities, List<Product> products, User agent, LocalDateTime when) {
        List<SaleItemRequest> items = new ArrayList<>();
        for (int p = 0; p < products.size(); p++) {
            if (quantities[p] > 0) {
                items.add(new SaleItemRequest(products.get(p).getId(), BigDecimal.valueOf(quantities[p])));
            }
        }
        if (!items.isEmpty()) {
            saleService.processSale(new SaleRequest(items, BigDecimal.ZERO), agent, when);
        }
    }
}
