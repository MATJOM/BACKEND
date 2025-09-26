package com.matjom.matjom.common.config;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {
	@Bean
	public DateTimeProvider kstDateTimeProvider() {
		return () -> Optional.of(OffsetDateTime.now(ZoneId.of("Asia/Seoul")));
	}
}
