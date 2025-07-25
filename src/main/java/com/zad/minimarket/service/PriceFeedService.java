package com.zad.minimarket.service;

import com.zad.minimarket.dto.PriceResponse;
import com.zad.minimarket.exception.PriceFeedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceFeedService {

    private final RestTemplate restTemplate;
    private final CacheService cacheService;
    private final Random random = new Random();

    @Value("${app.price-feed.base-url}")
    private String priceFeedBaseUrl;

    /**
     * Get current price for a symbol with retry logic and caching
     */
    public PriceResponse getCurrentPrice(String symbol) {
        log.debug("Getting price for symbol: {}", symbol);

        PriceResponse cachedPrice = cacheService.getCachedPriceFromRedis(symbol);
        if (cachedPrice != null) {
            log.debug("Returning cached price for symbol: {}", symbol);
            return cachedPrice;
        }

        PriceResponse price = fetchPriceWithRetry(symbol);

        // Cache the price for 3 seconds
        cacheService.cachePrice(symbol, price, java.time.Duration.ofSeconds(3));

        return price;
    }

    /**
     * Fetch price with retry mechanism using configurable retry parameters
     */
    @Retryable(
        retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
        noRetryFor = {HttpClientErrorException.class},
        maxAttemptsExpression = "${app.price-feed.retry.max-attempts:4}",
        backoff = @Backoff(
            delayExpression = "${app.price-feed.retry.initial-delay:1000}",
            multiplierExpression = "${app.price-feed.retry.multiplier:2.0}",
            maxDelayExpression = "${app.price-feed.retry.max-delay:8000}"
        )
    )
    public PriceResponse fetchPriceWithRetry(String symbol) {
        log.debug("Attempting to fetch price for symbol: {}", symbol);
        return fetchPrice(symbol);
    }

    /**
     * Recovery method called when all retry attempts fail
     */
    @Recover
    public PriceResponse recoverFromPriceFetch(Exception ex, String symbol) {
        log.warn("All retry attempts failed for symbol: {}. Using mock price. Error: {}",
            symbol, ex.getMessage());
        return generateMockPrice(symbol);
    }

    /**
     * Fetch price from external service
     */
    private PriceResponse fetchPrice(String symbol) {
        String url = priceFeedBaseUrl + "/price?symbol=" + symbol;
        log.debug("Fetching price from: {}", url);

        ResponseEntity<PriceResponse> response = restTemplate.getForEntity(url, PriceResponse.class);

        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            log.info("Fetched price for {}: {}", symbol, response.getBody().getPrice());
            return response.getBody();
        } else {
            throw new PriceFeedException("Invalid response from price feed service");
        }
    }

    /**
     * Generate mock price for testing purposes
     */
    private PriceResponse generateMockPrice(String symbol) {
        BigDecimal basePrice = getBasePriceForSymbol(symbol);

        // Add some random variation (-5% to +5%)
        double variation = (random.nextDouble() - 0.5) * 0.1;
        BigDecimal price = basePrice.multiply(BigDecimal.valueOf(1 + variation));
        price = price.setScale(6, RoundingMode.HALF_UP);

        log.debug("Generated mock price for {}: {}", symbol, price);

        PriceResponse priceResponse = new PriceResponse();
        priceResponse.setPrice(price);
        priceResponse.setSymbol(symbol);
        return priceResponse;
    }

    private BigDecimal getBasePriceForSymbol(String symbol) {
        return switch (symbol.toUpperCase()) {
            case "AAPL" -> BigDecimal.valueOf(210.55);
            case "GOOGL" -> BigDecimal.valueOf(2800.75);
            case "MSFT" -> BigDecimal.valueOf(415.30);
            case "TSLA" -> BigDecimal.valueOf(245.80);
            case "AMZN" -> BigDecimal.valueOf(3200.45);
            default -> BigDecimal.valueOf(100.00);
        };
    }
}

