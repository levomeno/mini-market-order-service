package com.zad.minimarket.service;

import com.zad.minimarket.dto.PriceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class CacheService {
    
    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);
    private static final String PRICE_CACHE_PREFIX = "price:";

    private final RedisTemplate<String, Object> redisTemplate;
    
    public CacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Store price in cache with TTL
     */
    public void cachePrice(String symbol, PriceResponse price, Duration ttl) {
        String key = PRICE_CACHE_PREFIX + symbol;
        redisTemplate.opsForValue().set(key, price, ttl.toSeconds(), TimeUnit.SECONDS);
        logger.debug("Cached price for symbol {} with TTL: {}", symbol, ttl);
    }
    
    /**
     * Get cached price directly from Redis
     */
    public PriceResponse getCachedPriceFromRedis(String symbol) {
        String key = PRICE_CACHE_PREFIX + symbol;
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached instanceof PriceResponse) {
            logger.debug("Cache hit for price: {}", symbol);
            return (PriceResponse) cached;
        }
        
        logger.debug("Cache miss for price: {}", symbol);
        return null;
    }
    
    /**
     * Evict price cache for a symbol
     */
    @CacheEvict(value = "prices", key = "#symbol")
    public void evictPriceCache(String symbol) {
        String key = PRICE_CACHE_PREFIX + symbol;
        redisTemplate.delete(key);
        logger.debug("Evicted price cache for symbol: {}", symbol);
    }

}

