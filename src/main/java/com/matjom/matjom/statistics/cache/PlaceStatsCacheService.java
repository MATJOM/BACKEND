package com.matjom.matjom.statistics.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.statistics.dto.PlaceStatsResponseDTO;
import com.matjom.matjom.statistics.dto.StatsDataSource;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceStatsCacheService {

    private static final String KEY_PREFIX = "place:stats:"; // 9월 26일 최종: 통계 캐시 키 프리픽스

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 캐시된 DTO가 있으면 반환하고 데이터 출처를 CACHE로 표시한다.
    public Optional<PlaceStatsResponseDTO> get(Long placeId) {
        String cached = redisTemplate.opsForValue().get(key(placeId));
        if (cached == null) {
            return Optional.empty();
        }

        try {
            PlaceStatsResponseDTO dto = objectMapper.readValue(cached, PlaceStatsResponseDTO.class);
            return Optional.of(dto.withDataSource(StatsDataSource.CACHE));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to deserialize place stats cache for placeId={}", placeId, ex);
            redisTemplate.delete(key(placeId));
            return Optional.empty();
        }
    }

    // 새로 계산한 DTO를 TTL과 함께 저장해 반복 조회 속도를 유지한다.
    public void put(Long placeId, PlaceStatsResponseDTO response) {
        long ttlSeconds = Math.max(response.getCacheTtlSeconds(), 1L); // 9월 26일 최종: 최소 TTL 1초 보장
        try {
            PlaceStatsResponseDTO payload = response.withDataSource(StatsDataSource.CACHE);
            String serialized = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForValue().set(key(placeId), serialized, Duration.ofSeconds(ttlSeconds)); // 9월 29일 최종: 키는 placeId, 응답은 placeName 포함
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize place stats cache for placeId={}", placeId, ex);
        }
    }

    // 다음 요청에서 최신 값을 계산하도록 캐시 항목을 제거한다.
    public void evict(Long placeId) {
        redisTemplate.delete(key(placeId));
    }

    // 장소 통계 캐시에 사용할 Redis 키를 생성한다.
    private String key(Long placeId) {
        return KEY_PREFIX + placeId;
    }
}
