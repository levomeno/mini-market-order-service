package com.zad.minimarket.service;

import com.zad.minimarket.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RateLimitingService {

    private final int requestsPerSecond;
    private final int bucketCapacity;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final RedisTemplate<String, Object> redisTemplate;

    public RateLimitingService(@Value("${app.rate-limit.requests-per-second}") int requestsPerSecond,
                              @Value("${app.rate-limit.bucket-capacity}") int bucketCapacity,
                              RedisTemplate<String, Object> redisTemplate) {
        this.requestsPerSecond = requestsPerSecond;
        this.bucketCapacity = bucketCapacity;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if request is allowed for the given account ID
     */
    public void checkRateLimit(String accountId) {
        Bucket bucket = getBucketForAccount(accountId);

        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for account: {}", accountId);
            throw new RateLimitExceededException("Rate limit exceeded for account: " + accountId);
        }

        log.debug("Rate limit check passed for account: {}", accountId);
    }

    /**
     * Get or create bucket for account
     */
    private Bucket getBucketForAccount(String accountId) {
        return buckets.computeIfAbsent(accountId, this::createBucket);
    }

    /**
     * Create new bucket with configured limits
     */
    private Bucket createBucket(String accountId) {
        log.debug("Creating new rate limit bucket for account: {}", accountId);

        Bandwidth limit = Bandwidth.classic(bucketCapacity, Refill.intervally(requestsPerSecond, Duration.ofSeconds(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Get remaining tokens for account
     */
    public long getRemainingTokens(String accountId) {
        Bucket bucket = getBucketForAccount(accountId);
        return bucket.getAvailableTokens();
    }

    /**
     * Reset rate limit for account (for testing purposes)
     */
    public void resetRateLimit(String accountId) {
        buckets.remove(accountId);
        log.debug("Reset rate limit for account: {}", accountId);
    }
}

