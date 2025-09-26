package com.matjom.matjom.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.matjom.matjom.common.exception.base.RecommendationException;
import com.matjom.matjom.place.repository.PlaceRepository;
import com.matjom.matjom.recommendation.dto.RouletteCandidate;
import com.matjom.matjom.recommendation.dto.RouletteRequest;
import com.matjom.matjom.recommendation.dto.RouletteResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouletteServiceTest {

    @Mock
    private PlaceRepository placeRepository;

    private RouletteService rouletteService;

    @BeforeEach
    void setUp() {
        rouletteService = new RouletteService(placeRepository);
    }

    @Test
    void recommendSelectsCandidateDeterministicallyWithSeed() {
        RouletteRequest request = buildRequest();
        request.setSeed(42L);

        List<RouletteCandidate> candidates = List.of(
                new RouletteCandidate(1L, "A", 10.0, List.of("korean")),
                new RouletteCandidate(2L, "B", 20.0, List.of("japanese")),
                new RouletteCandidate(3L, "C", 30.0, List.of("chinese"))
        );
        when(placeRepository.findRouletteCandidates(anyDouble(), anyDouble(), anyDouble(), anyList(), anyInt()))
                .thenReturn(candidates);

        RouletteResponse response = rouletteService.recommend(request, "key-1");

        assertThat(response.placeId()).isEqualTo(3L);
        assertThat(response.meta().candidateCount()).isEqualTo(3);
        assertThat(response.meta().replayed()).isFalse();
    }

    @Test
    void recommendThrowsWhenNoCandidates() {
        RouletteRequest request = buildRequest();
        when(placeRepository.findRouletteCandidates(anyDouble(), anyDouble(), anyDouble(), anyList(), anyInt()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> rouletteService.recommend(request, "key-2"))
                .isInstanceOf(RecommendationException.class);
    }

    private RouletteRequest buildRequest() {
        RouletteRequest request = new RouletteRequest();
        request.setLat(37.5);
        request.setLng(127.0);
        request.setRadius(300.0);
        request.setLimit(50);
        request.setCategories(List.of("korean"));
        return request;
    }
}
