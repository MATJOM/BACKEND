package com.matjom.matjom.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matjom.matjom.common.exception.base.SearchException;
import com.matjom.matjom.place.dto.PlaceSearchCursor;
import com.matjom.matjom.place.dto.PlaceSearchRequest;
import com.matjom.matjom.place.dto.PlaceSearchResponse;
import com.matjom.matjom.place.dto.PlaceSearchResponse.PlaceSummary;
import com.matjom.matjom.place.repository.PlaceRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
        verify(placeRepository, never()).search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }

    @Test
    void cachesResultWhenMiss() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> summaries = List.of(new PlaceSummary(2L, "Another", 45.6));
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(summaries);

        PlaceSearchResponse response = placeSearchService.search(buildRequest());

        assertThat(response.places()).containsExactlyElementsOf(summaries);
        verify(placeRepository).search(anyDouble(), anyDouble(), anyDouble(), eq(501), isNull(), eq((String) null));
        verify(valueOperations).set(anyString(), anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    void createsNextCursorWhenLimitReached() {
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> summaries = List.of(
                new PlaceSummary(10L, "First", 12.34567),
                new PlaceSummary(20L, "Second", 45.67891),
                new PlaceSummary(30L, "Third", 78.90123)
        );
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(summaries);

        PlaceSearchRequest request = buildRequest();
        request.setSize(2);

        PlaceSearchResponse response = placeSearchService.search(request);

        assertThat(response.places()).hasSize(2);
        assertThat(response.places())
                .extracting(PlaceSummary::placeId)
                .containsExactly(10L, 20L);
        assertThat(response.nextCursor()).isEqualTo("45.67891:20");
    }

    @Test
    void doesNotCreateNextCursorWhenResultsLessThanRequestedSize() {
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> summaries = List.of(
                new PlaceSummary(10L, "First", 12.34567),
                new PlaceSummary(20L, "Second", 45.67891)
        );
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(summaries);

        PlaceSearchRequest request = buildRequest();
        request.setSize(5);

        PlaceSearchResponse response = placeSearchService.search(request);

        assertThat(response.places()).hasSize(2);
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void attachesTooManyResultsMetaWhenMoreThanMaxLimit() {
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> summaries = IntStream.rangeClosed(1, 501)
                .mapToObj(i -> new PlaceSummary((long) i, "Place " + i, (double) i))
                .toList();
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(summaries);

        PlaceSearchRequest request = buildRequest();
        request.setSize(100);

        PlaceSearchResponse response = placeSearchService.search(request);

        assertThat(response.places()).hasSize(100);
        assertThat(response.nextCursor()).isEqualTo(PlaceSearchCursor.toToken(100.0, 100L));
        assertThat(response.meta()).isNotNull();
        assertThat(response.meta().reason()).isEqualTo("too_many_results");
        assertThat(response.meta().suggest()).isNotBlank();
    }

    @Test
    void addsLowResultsSuggestionWhenResultsBelowThreshold() {
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> dataset = IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new PlaceSummary((long) i, "Place " + i, (double) i))
                .toList();
        configureRepositoryDataset(dataset);

        PlaceSearchResponse response = placeSearchService.search(buildRequest());

        assertThat(response.places()).hasSize(5);
        assertThat(response.meta()).isNotNull();
        assertThat(response.meta().reason()).isEqualTo("low_results");
        assertThat(response.meta().suggest()).contains("반경");
    }

    @Test
    void paginatesAcrossPagesWithoutDuplicates() {
        when(valueOperations.get(anyString())).thenReturn(null);
        List<PlaceSummary> dataset = IntStream.rangeClosed(1, 25)
                .mapToObj(i -> new PlaceSummary((long) i, "Place " + i, i * 10.0))
                .toList();
        configureRepositoryDataset(dataset);

        List<Long> collectedIds = new ArrayList<>();
        PlaceSearchRequest request = buildRequest();
        int safety = 0;
        while (safety++ < 5) {
            PlaceSearchResponse response = placeSearchService.search(request);
            response.places().stream()
                    .map(PlaceSummary::placeId)
                    .forEach(collectedIds::add);

            if (response.nextCursor() == null) {
                break;
            }
            request = buildRequest();
            request.setCursor(response.nextCursor());
        }

        assertThat(collectedIds).doesNotContainNull();
        assertThat(collectedIds).doesNotHaveDuplicates();
        assertThat(collectedIds).containsExactlyElementsOf(
                dataset.stream().map(PlaceSummary::placeId).toList());
    }

    @Test
    void parsesCursorAndPassesToRepository() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenReturn(List.of());

        PlaceSearchRequest request = buildRequest();
        request.setCursor("123.45:99");

        placeSearchService.search(request);

        ArgumentCaptor<PlaceSearchCursor> cursorCaptor = ArgumentCaptor.forClass(PlaceSearchCursor.class);
        verify(placeRepository).search(anyDouble(), anyDouble(), anyDouble(), anyInt(), cursorCaptor.capture(), eq((String) null));
        assertThat(cursorCaptor.getValue().distanceMeters()).isEqualTo(123.45);
        assertThat(cursorCaptor.getValue().lastPlaceId()).isEqualTo(99L);
    }

    @Test
    void throwsExceptionWhenCursorInvalid() {
        PlaceSearchRequest request = buildRequest();
        request.setCursor("invalid");

        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> placeSearchService.search(request)))
                .isInstanceOf(SearchException.class)
                .hasMessageContaining("cursor");
    }

    private PlaceSearchRequest buildRequest() {
        PlaceSearchRequest request = new PlaceSearchRequest();
        request.setLat(37.5665);
        request.setLng(126.9780);
        // radius와 size는 null -> 기본값 사용
        return request;
    }

    private void configureRepositoryDataset(List<PlaceSummary> dataset) {
        when(placeRepository.search(anyDouble(), anyDouble(), anyDouble(), anyInt(), any(), any()))
                .thenAnswer(invocation -> {
                    int limit = invocation.getArgument(3);
                    PlaceSearchCursor cursor = invocation.getArgument(4);
                    double cursorDistance = cursor == null ? Double.NEGATIVE_INFINITY : cursor.distanceMeters();
                    long cursorId = cursor == null ? Long.MIN_VALUE : cursor.lastPlaceId();

                    return dataset.stream()
                            .filter(summary -> summary.distanceMeters() > cursorDistance
                                    || (summary.distanceMeters() == cursorDistance && summary.placeId() > cursorId))
                            .limit(limit)
                            .toList();
                });
    }
}
