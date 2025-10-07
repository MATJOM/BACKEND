package com.matjom.matjom.visit.service;

import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.user.entity.User;
import java.util.Collections;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class VisitPrivilegeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VisitPrivilegeService.class);
    private static final String RATE_LIMIT_USER_KEY_PREFIX = "rl:places:user:";

    private final StringRedisTemplate stringRedisTemplate;

    public VisitPrivilegeService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate");
    }

    public void resetSearchQuotaForArrival(Visit visit) {
        if (visit == null) {
            return;
        }
        User visitUser = visit.getUser();
        if (visitUser == null) {
            return;
        }
        UUID userId = visitUser.getId();
        if (userId == null) {
            return;
        }
        String key = RATE_LIMIT_USER_KEY_PREFIX + userId;
        try {
            stringRedisTemplate.delete(Collections.singletonList(key));
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to reset rate limit key for user {}", userId, ex);
            throw ex;
        }
    }
}
