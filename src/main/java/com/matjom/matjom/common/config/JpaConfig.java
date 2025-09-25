package com.matjom.matjom.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider") // 수정제안 2024-09-24: Auditing에 OffsetDateTime 공급자를 명시.
public class JpaConfig {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul"); // 수정제안 2024-09-24: 공통 타임존 상수.

    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        // 수정제안 2024-09-24: BaseEntity가 요구하는 OffsetDateTime을 Auditing에 전달.
        return () -> Optional.of(OffsetDateTime.now(KST));
    }
}