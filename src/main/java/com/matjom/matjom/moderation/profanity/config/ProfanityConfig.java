package com.matjom.matjom.moderation.profanity.config;

import com.matjom.matjom.moderation.profanity.HardcodedProfanityFilter;
import com.matjom.matjom.moderation.profanity.ProfanityFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 금칙어 필터 빈 구성을 담당하는 설정 클래스.
 * 사용 목적: 애플리케이션 컨텍스트에 {@link ProfanityFilter} 구현체를 등록한다.
 * 코드 의미: 하드코딩된 필터 구현을 빈으로 제공해 서비스에서 주입받게 한다.
 * 기대 결과: 컨텍스트 초기화 시 금칙어 필터 빈이 생성되어 전역에서 재사용된다.
 */
@Configuration
public class ProfanityConfig {
    /**
     * 금칙어 필터 빈을 생성한다.
     * 사용 목적: 서비스/컨트롤러가 의존성 주입으로 필터를 사용하도록 한다.
     * 코드 의미: `HardcodedProfanityFilter` 인스턴스를 반환한다.
     * 기대 결과: 컨테이너에서 싱글톤 필터를 관리하며 재사용한다.
     */
    @Bean
    public ProfanityFilter profanityFilter() {
        return new HardcodedProfanityFilter();
    }
}
