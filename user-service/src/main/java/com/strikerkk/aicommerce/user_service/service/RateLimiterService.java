package com.strikerkk.aicommerce.user_service.service;


import com.strikerkk.aicommerce.user_service.exception.TooManyAttemptsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int WINDOW_MINUTES = 60;
    private final StringRedisTemplate stringRedisTemplate;

    public void allowAndRecordAttempts(String key) {
        String redisKey = redisKey(key);
        Long count = stringRedisTemplate.opsForValue().increment(redisKey);

        if(count != null && count == 1L) {
            stringRedisTemplate.expire(redisKey, Duration.ofMinutes(WINDOW_MINUTES));
        }

        if(count != null && count > MAX_ATTEMPTS) {
            log.warn("Rate limit exceed for key={}", redisKey);
            throw new TooManyAttemptsException("Too many login attempts. Try again later.");
        }
    }

    public void resetAttempts(String key) {
        String redisKey = redisKey(key);
        stringRedisTemplate.delete(redisKey);
        log.info("Rate limit counter reset for key={}", redisKey);
    }

    private String redisKey(String key) {
        return "rate_limit:" + key;
    }

}
