package com.matjom.matjom.statistics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class DailyStatsPredictionService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String endpoint;

    public DailyStatsPredictionService(RestTemplateBuilder restTemplateBuilder,
                                       ObjectMapper objectMapper,
                                       @Value("${statistics.prediction.enabled:false}") boolean enabled,
                                       @Value("${statistics.prediction.endpoint:http://localhost:8080/internal/predictions/visit}") String endpoint) {
        this.restTemplate = restTemplateBuilder.build();
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.endpoint = endpoint;
    }

    // 예측 기능이 활성화되었을 때 집계 요약을 외부 예측 엔진으로 전달한다.
    public void scheduleRetraining(LocalDate targetDate,
                                   Map<Long, DailyStatsBatchService.DailyStatsSummary> summaries) {
        if (!enabled) {
            log.debug("Prediction retraining disabled. Skipping trigger for {}", targetDate);
            return;
        }
        if (summaries.isEmpty()) {
            log.debug("No summaries to sync for {}. Skipping prediction retraining", targetDate);
            return;
        }

        PredictionRequest payload = new PredictionRequest(targetDate, summaries);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            byte[] body = objectMapper.writeValueAsBytes(payload);
            HttpEntity<byte[]> request = new HttpEntity<>(body, headers);

            ResponseEntity<Void> response = restTemplate.postForEntity(URI.create(endpoint), request, Void.class);
            log.info("Triggered prediction retraining for {} (status: {})", targetDate, response.getStatusCode());
        } catch (RestClientException ex) {
            log.warn("Failed to trigger prediction retraining for {}: {}", targetDate, ex.getMessage(), ex);
        } catch (Exception ex) {
            log.warn("Unexpected error while triggering prediction retraining for {}", targetDate, ex);
        }
    }

    public record PredictionRequest(LocalDate targetDate,
                                    Map<Long, DailyStatsBatchService.DailyStatsSummary> summaries) {}
}
