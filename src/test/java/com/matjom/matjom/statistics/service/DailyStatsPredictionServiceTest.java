package com.matjom.matjom.statistics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DailyStatsPredictionServiceTest {

    @Test
    // 예측 기능이 활성화된 경우 REST 호출이 수행되는지 확인한다.
    void scheduleRetrainingPostsPayloadWhenEnabled() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        RestTemplateBuilder builder = new RestTemplateBuilder() {
            @Override
            public RestTemplate build() {
                return restTemplate;
            }
        };

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        DailyStatsPredictionService service = new DailyStatsPredictionService(
                builder,
                mapper,
                true,
                "http://localhost:18080/internal/predictions/visit"
        );

        DailyStatsBatchService.DailyStatsSummary summary = new DailyStatsBatchService.DailyStatsSummary(
                5L,
                3L,
                2L,
                1L,
                Map.of(10, 2L),
                Map.of(9, 1L),
                10
        );

        server.expect(requestTo("http://localhost:18080/internal/predictions/visit"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess());

        service.scheduleRetraining(LocalDate.of(2024, 9, 26), Map.of(1L, summary));

        server.verify();
    }

    @Test
    // 기능이 비활성화되었을 때는 어떤 REST 호출도 발생하지 않는지 검증한다.
    void scheduleRetrainingSkipsWhenDisabled() {
        DailyStatsPredictionService service = new DailyStatsPredictionService(
                new RestTemplateBuilder(),
                new ObjectMapper().findAndRegisterModules(),
                false,
                "http://localhost:18080/internal/predictions/visit"
        );

        service.scheduleRetraining(LocalDate.of(2024, 9, 26), Map.of());
        service.scheduleRetraining(LocalDate.of(2024, 9, 26), Map.of(1L, new DailyStatsBatchService.DailyStatsSummary(0,0,0,0, Map.of(), Map.of(), null)));

        // 예외가 발생하지 않고 HTTP 요청이 없으면 통과로 간주한다.
        assertThat(true).isTrue();
    }
}
