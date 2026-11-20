package com.freshproduce.pos.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshproduce.pos.domain.Product;
import com.freshproduce.pos.domain.SaleItem;
import com.freshproduce.pos.domain.SalesForecast;
import com.freshproduce.pos.domain.User;
import com.freshproduce.pos.dto.Requests.ForecastRequest;
import com.freshproduce.pos.dto.Responses.DailyPoint;
import com.freshproduce.pos.dto.Responses.ForecastDto;
import com.freshproduce.pos.exception.BusinessException;
import com.freshproduce.pos.exception.ServiceUnavailableException;
import com.freshproduce.pos.repository.SaleItemRepository;
import com.freshproduce.pos.repository.SalesForecastRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Forecasting Service Client (FR-05). Sends the product, forecast period and daily sales history to the
 * Python/FastAPI AI service and stores the returned prediction. If the AI service is down, only this
 * feature is affected – POS and inventory functions keep working.
 */
@Service
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);
    private static final int HISTORY_DAYS = 180;
    private static final String UNAVAILABLE =
            "The AI forecasting service is currently unavailable. POS sales and inventory functions continue "
            + "to work normally. Please try the forecast again later.";

    private final RestClient restClient;
    private final ProductService productService;
    private final SaleItemRepository saleItemRepository;
    private final SalesForecastRepository forecastRepository;
    private final ObjectMapper objectMapper;

    public ForecastService(RestClient.Builder builder,
                           @Value("${app.ai.base-url}") String baseUrl,
                           @Value("${app.ai.api-key}") String apiKey,
                           @Value("${app.ai.timeout-ms:10000}") int timeoutMs,
                           ProductService productService, SaleItemRepository saleItemRepository,
                           SalesForecastRepository forecastRepository, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(timeoutMs);
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("X-API-Key", apiKey).build();
        this.productService = productService;
        this.saleItemRepository = saleItemRepository;
        this.forecastRepository = forecastRepository;
        this.objectMapper = objectMapper;
    }

    /** Body returned by POST /api/forecast on the AI service. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiForecastResponse(Long productId, BigDecimal predictedQuantity, List<DailyPoint> dailyForecast,
                                     String modelType, BigDecimal mae, BigDecimal baselineMae,
                                     BigDecimal accuracyPercent, String dataSource) { }

    @Transactional
    public ForecastDto generate(ForecastRequest request, User user) {
        Product product = productService.find(request.productId());
        if (!product.isActive()) {
            throw new BusinessException(product.getName() + " is inactive. Select a valid product.");
        }
        int period = request.forecastPeriodDays();
        LocalDate today = LocalDate.now();
        SortedMap<LocalDate, BigDecimal> history = dailyHistory(product.getId(), today);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", product.getId());
        body.put("productName", product.getName());
        body.put("forecastPeriodDays", period);
        body.put("startDate", today.toString());
        body.put("history", history.entrySet().stream()
                .map(e -> Map.of("date", e.getKey().toString(), "quantity", e.getValue()))
                .toList());

        AiForecastResponse response = callAiService(body);
        if (response == null || response.predictedQuantity() == null) {
            throw new ServiceUnavailableException(UNAVAILABLE);
        }

        SalesForecast forecast = new SalesForecast();
        forecast.setProduct(product);
        forecast.setPeriodStart(today);
        forecast.setPeriodEnd(today.plusDays(period - 1L));
        forecast.setForecastPeriodDays(period);
        forecast.setPredictedQuantity(response.predictedQuantity().setScale(2, RoundingMode.HALF_UP));
        forecast.setModelType(response.modelType());
        forecast.setModelMae(scale(response.mae()));
        forecast.setBaselineMae(scale(response.baselineMae()));
        forecast.setAccuracyPercent(scale(response.accuracyPercent()));
        forecast.setDailyPredictionsJson(toJson(response.dailyForecast()));
        forecast.setRequestedBy(user);
        forecast = forecastRepository.save(forecast);

        List<DailyPoint> recent = history.entrySet().stream()
                .skip(Math.max(0, history.size() - 14L))
                .map(e -> new DailyPoint(e.getKey(), e.getValue()))
                .toList();
        return toDto(forecast, recent);
    }

    @Transactional(readOnly = true)
    public List<ForecastDto> history(Long productId) {
        List<SalesForecast> list = productId == null
                ? forecastRepository.findTop20ByOrderByCreatedDateDesc()
                : forecastRepository.findTop20ByProductIdOrderByCreatedDateDesc(productId);
        return list.stream().map(f -> toDto(f, List.of())).toList();
    }

    /** Daily quantity sold for the product over the last 180 complete days (days without sales count as 0). */
    SortedMap<LocalDate, BigDecimal> dailyHistory(Long productId, LocalDate today) {
        LocalDate from = today.minusDays(HISTORY_DAYS);
        SortedMap<LocalDate, BigDecimal> totals = new TreeMap<>();
        for (SaleItem item : saleItemRepository.findForProductSince(productId, from.atStartOfDay())) {
            LocalDate day = item.getSale().getSaleDate().toLocalDate();
            if (day.isBefore(today)) {
                totals.merge(day, item.getQuantity(), BigDecimal::add);
            }
        }
        if (totals.isEmpty()) {
            return totals;
        }
        for (LocalDate d = totals.firstKey(); d.isBefore(today); d = d.plusDays(1)) {
            totals.putIfAbsent(d, BigDecimal.ZERO);
        }
        return totals;
    }

    private AiForecastResponse callAiService(Map<String, Object> body) {
        try {
            return restClient.post().uri("/api/forecast")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(AiForecastResponse.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new BusinessException("Forecast request rejected: " + extractMessage(e.getResponseBodyAsString()));
            }
            log.warn("AI service returned {}", e.getStatusCode());
            throw new ServiceUnavailableException(UNAVAILABLE);
        } catch (ResourceAccessException e) {
            log.warn("AI service unreachable: {}", e.getMessage());
            throw new ServiceUnavailableException(UNAVAILABLE);
        } catch (RestClientException e) {
            log.warn("AI service call failed: {}", e.getMessage());
            throw new ServiceUnavailableException(UNAVAILABLE);
        }
    }

    private String extractMessage(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.hasNonNull("message")) {
                return node.get("message").asText();
            }
            if (node.hasNonNull("detail")) {
                return node.get("detail").isTextual() ? node.get("detail").asText() : node.get("detail").toString();
            }
        } catch (JsonProcessingException ignored) {
            // fall through
        }
        return "invalid input";
    }

    private ForecastDto toDto(SalesForecast f, List<DailyPoint> recentHistory) {
        Product p = f.getProduct();
        return new ForecastDto(f.getId(), p.getId(), p.getName(), p.getUnitOfMeasure(), p.getSellingPrice(),
                f.getPeriodStart(), f.getPeriodEnd(), f.getForecastPeriodDays(), f.getPredictedQuantity(),
                f.getPredictedQuantity().multiply(p.getSellingPrice()).setScale(2, RoundingMode.HALF_UP),
                f.getModelType(), f.getModelMae(), f.getBaselineMae(), f.getAccuracyPercent(),
                fromJson(f.getDailyPredictionsJson()), recentHistory, f.getCreatedDate(),
                f.getRequestedBy() == null ? null : f.getRequestedBy().getFullName());
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String toJson(List<DailyPoint> points) {
        try {
            return objectMapper.writeValueAsString(points == null ? List.of() : points);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<DailyPoint> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<DailyPoint>>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
