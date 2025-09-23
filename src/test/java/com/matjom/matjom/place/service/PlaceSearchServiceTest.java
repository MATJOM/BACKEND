package com.matjom.matjom.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.place.dto.PlaceSearchRequest;
import com.matjom.matjom.place.dto.PlaceSearchResponse;
import com.matjom.matjom.place.dto.PlaceSearchResponse.PlaceSummary;
import com.matjom.matjom.place.repository.PlaceRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class PlaceSearchServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PlaceSearchService placeSearchService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        placeSearchService = new PlaceSearchService(placeRepository, redisTemplate, objectMapper);
    }

    @Test
    void returnsCachedResultWhenHit() throws Exception {
        PlaceSearchResponse cached = new PlaceSearchResponse(
                List.of(new PlaceSummary(1L, "Place", 123.4)),
                "cursor"
        );
        when(valueOperations.get(anyString())).thenReturn(objectMapper.writeValueAsString(cached));

        PlaceSearchResponse result = placeSearchService.search(buildRequest());

        assertThat(result).isEqualTo(cached);
        verify(placeRepository, never()).search(anyDouble(), anyDouble(), anyDouble(), anyInt(), anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }

    @Test
    void cachesResultWhenMiss() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> summaries = List.of(new PlaceSummary(2L, "Another", 45.6));
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any()))
                .thenReturn(summaries);

        PlaceSearchResponse response = placeSearchService.search(buildRequest());

        assertThat(response.places()).containsExactlyElementsOf(summaries);
        verify(placeRepository).search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any());
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(60)));
    }

    private PlaceSearchRequest buildRequest() {
        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setLat(37.5665);
        request.setLng(126.9780);
        // radius와 size는 null -> 기본값 사용
        return request;
    }
}
