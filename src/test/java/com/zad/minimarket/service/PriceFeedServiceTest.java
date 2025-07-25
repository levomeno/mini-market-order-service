package com.zad.minimarket.service;

import com.zad.minimarket.dto.PriceResponse;
import com.zad.minimarket.exception.PriceFeedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

class PriceFeedServiceTest {

    @InjectMocks
    private PriceFeedService priceFeedService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CacheService cacheService;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        Field baseUrlField = PriceFeedService.class.getDeclaredField("priceFeedBaseUrl");
        baseUrlField.setAccessible(true);
        baseUrlField.set(priceFeedService, "http://mock-price-feed");
    }

    @Test
    void should_ReturnCachedPrice_When_CacheExists() {
        // Given
        String symbol = "AAPL";
        PriceResponse cached = new PriceResponse();
        cached.setSymbol(symbol);
        cached.setPrice(BigDecimal.valueOf(210.55));
        when(cacheService.getCachedPriceFromRedis(symbol)).thenReturn(cached);

        // When
        PriceResponse result = priceFeedService.getCurrentPrice(symbol);

        // Then
        verify(restTemplate, never()).getForEntity(anyString(), eq(PriceResponse.class));
        Assertions.assertEquals(cached.getPrice(), result.getPrice());
    }

    @Test
    void should_FetchAndCachePrice_When_CacheDoesNotExist() {
        // Given
        String symbol = "GOOGL";
        when(cacheService.getCachedPriceFromRedis(symbol)).thenReturn(null);

        PriceResponse remote = new PriceResponse();
        remote.setPrice(BigDecimal.valueOf(2800.75));
        remote.setSymbol(symbol);
        ResponseEntity<PriceResponse> response = new ResponseEntity<>(remote, HttpStatus.OK);
        when(restTemplate.getForEntity("http://mock-price-feed/price?symbol=" + symbol, PriceResponse.class))
            .thenReturn(response);

        // When
        PriceResponse result = priceFeedService.getCurrentPrice(symbol);

        // Then
        verify(cacheService).cachePrice(eq(symbol), eq(remote), eq(Duration.ofSeconds(3)));
        Assertions.assertEquals(remote.getPrice(), result.getPrice());
    }

    @Test
    void should_FetchPriceSuccessfully_When_ServiceRespondsOK() {
        // Given
        String symbol = "MSFT";
        PriceResponse response = new PriceResponse();
        response.setPrice(BigDecimal.valueOf(415.30));
        response.setSymbol(symbol);
        when(restTemplate.getForEntity(anyString(), eq(PriceResponse.class)))
            .thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        // When
        PriceResponse result = priceFeedService.fetchPriceWithRetry(symbol);

        // Then
        Assertions.assertEquals(response.getPrice(), result.getPrice());
    }

    @Test
    void should_ReturnMockPrice_When_AllRetryAttemptsFail() {
        // Given
        String symbol = "TSLA";
        HttpServerErrorException exception = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);

        // When
        PriceResponse fallback = priceFeedService.recoverFromPriceFetch(exception, symbol);

        // Then
        Assertions.assertNotNull(fallback);
        Assertions.assertEquals(symbol, fallback.getSymbol());
        Assertions.assertEquals(6, fallback.getPrice().scale());
    }

    @Test
    void should_ThrowException_When_PriceFeedResponseIsInvalid() {
        // Given
        String symbol = "AMZN";
        ResponseEntity<PriceResponse> emptyResponse = new ResponseEntity<>(null, HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(PriceResponse.class))).thenReturn(emptyResponse);

        // Then
        assertThrows(PriceFeedException.class, () -> {
            priceFeedService.fetchPriceWithRetry(symbol);
        });
    }
}
