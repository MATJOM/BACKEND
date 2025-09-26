package com.matjom.matjom.moderation.profanity.config;

import com.matjom.matjom.moderation.profanity.HardcodedProfanityFilter;
import com.matjom.matjom.moderation.profanity.ProfanityFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class ProfanityConfig {
    @Bean
    public ProfanityFilter profanityFilter() {
        return new HardcodedProfanityFilter();
    }
    // 9월26일 수정제안: ReviewModerationService에서 주입받을 Clock 빈을 제공합니다.
    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}