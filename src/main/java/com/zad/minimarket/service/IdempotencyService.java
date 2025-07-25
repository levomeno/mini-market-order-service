package com.zad.minimarket.service;

import com.zad.minimarket.dto.OrderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final Duration IDEMPOTENCY_KEY_TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isKeyProcessed(String idempotencyKey) {
        return redisTemplate.hasKey(IDEMPOTENCY_KEY_PREFIX + idempotencyKey);
    }

    public void saveOrderResponse(String idempotencyKey, OrderResponse orderResponse) {
        try {
            String responseJson = objectMapper.writeValueAsString(orderResponse);
            redisTemplate.opsForValue().set(IDEMPOTENCY_KEY_PREFIX + idempotencyKey, responseJson, IDEMPOTENCY_KEY_TTL);
        } catch (Exception e) {
            // Log error
            throw new RuntimeException("Failed to save idempotency key", e);
        }
    }

    public OrderResponse getProcessedOrderResponse(String idempotencyKey) {
        try {
            String responseJson = redisTemplate.opsForValue().get(IDEMPOTENCY_KEY_PREFIX + idempotencyKey);
            if (responseJson != null) {
                return objectMapper.readValue(responseJson, OrderResponse.class);
            }
            return null;
        } catch (Exception e) {
            // Log error
            throw new RuntimeException("Failed to retrieve idempotency key", e);
        }
    }

    public void removeKey(String idempotencyKey) {
        redisTemplate.delete(IDEMPOTENCY_KEY_PREFIX + idempotencyKey);
    }
}
