package com.otp_system.auth_service.Redis;


import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisOtpService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final long OTP_TTL_MINUTES = 5;

    @CircuitBreaker(name = "redisService", fallbackMethod = "fallbackVoid")
    @Retry(name = "redisRetry")
    public void saveOtp(String key, String value) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofMinutes(OTP_TTL_MINUTES));

        if (Boolean.FALSE.equals(success)) {
            log.info("OTP already exists for key {}. Skipping overwrite.", key);
            return; // do not throw exception; this is an expected scenario
        }
    }

    @CircuitBreaker(name = "redisService", fallbackMethod = "fallbackGet")
    @Retry(name = "redisRetry")
    public String getOtp(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @CircuitBreaker(name = "redisService", fallbackMethod = "fallbackVoid")
    @Retry(name = "redisRetry")
    public void deleteKey(String key) {
        redisTemplate.delete(key);
    }

    @CircuitBreaker(name = "redisService", fallbackMethod = "fallbackVoid")
    @Retry(name = "redisRetry")
    public void incrementAttempts(String key) {
        Long attempts = redisTemplate.opsForValue().increment(key);

        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(OTP_TTL_MINUTES));
        }
    }

    @CircuitBreaker(name = "redisService", fallbackMethod = "fallbackGetAttempts")
    @Retry(name = "redisRetry")
    public Long getAttempts(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }

    public String fallbackGet(String key, Throwable ex) {
        log.error("Redis failure for key {}: {}", key, ex.getMessage());
        throw new IllegalStateException("Redis temporarily unavailable");
    }

    public void fallbackVoid(String key, Throwable ex) {
        log.error("Redis failure for key {}: {}", key, ex.getMessage());
        throw new IllegalStateException("Redis temporarily unavailable");
    }

    public void fallbackVoid(String key, String value, Throwable ex) {
        log.error("Redis failure for key {} with value {}: {}", key, value, ex.getMessage());
        throw new IllegalStateException("Redis temporarily unavailable");
    }

    public Long fallbackGetAttempts(String key, Throwable ex) {
        log.error("Redis failure while fetching attempts for key {}: {}", key, ex.getMessage());
        throw new IllegalStateException("Redis temporarily unavailable");
    }
}