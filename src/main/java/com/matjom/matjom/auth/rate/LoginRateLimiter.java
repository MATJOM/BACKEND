package com.matjom.matjom.auth.rate;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class LoginRateLimiter {
    private static final String KEY_PREFIX = "login:fail:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    public boolean isCaptchaRequired(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        String attempts = redisTemplate.opsForValue().get(buildKey(email));
        if (!StringUtils.hasText(attempts)) {
            return false;
        }
        try {
            return Long.parseLong(attempts) >= MAX_ATTEMPTS;
        } catch (NumberFormatException ex) {
            redisTemplate.delete(buildKey(email));
            return false;
        }
    }

    public long recordFailure(String email) {
        if (!StringUtils.hasText(email)) {
            return 0L;
        }
        String key = buildKey(email);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
        return count == null ? 0L : count;
    }

    public void reset(String email) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        redisTemplate.delete(buildKey(email));
    }

    private String buildKey(String email) {
        return KEY_PREFIX + email;
    }
}
