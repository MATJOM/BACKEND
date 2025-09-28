package com.matjom.matjom.placefacade.service;

import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.service.ReviewService;
import com.matjom.matjom.placefacade.dto.PlaceDetailResponseDTO;
import com.matjom.matjom.statistics.dto.PlaceStatsResponseDTO;
import com.matjom.matjom.statistics.service.PlaceStatisticsQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceDetailFacadeService {

    private static final int DEFAULT_REVIEW_LIMIT = 5; // 9월 30일 최종: 기본으로 최근 5개 리뷰만 노출

    private final PlaceStatisticsQueryService placeStatisticsQueryService;
    private final ReviewService reviewService;

    // 장소 통계와 리뷰를 조합해 한 번에 반환하고 리뷰 노출 개수를 제어한다.
    public PlaceDetailResponseDTO getPlaceDetail(Long placeId, Integer reviewLimit) {
        int effectiveLimit = resolveLimit(reviewLimit);

        PlaceStatsResponseDTO statistics = placeStatisticsQueryService.getPlaceStats(placeId);
        List<ReviewResponseDTO> reviews = reviewService.getPlaceReviews(placeId);
        long totalReviewCount = reviews.size();

        List<ReviewResponseDTO> limitedReviews = applyLimit(reviews, effectiveLimit);

        return PlaceDetailResponseDTO.builder()
                .statistics(statistics)
                .reviews(limitedReviews)
                .totalReviewCount(totalReviewCount)
                .build();
    }

    // 요청된 리뷰 제한 값이 없거나 잘못된 경우 기본값을 적용한다.
    private int resolveLimit(Integer reviewLimit) {
        if (reviewLimit == null) {
            return DEFAULT_REVIEW_LIMIT;
        }
        if (reviewLimit <= 0) {
            return Integer.MAX_VALUE; // 0 이하 값은 제한 없이 모두 반환
        }
        return reviewLimit;
    }

    // 제한 개수에 맞춰 리뷰 리스트를 잘라내고, 부족하면 원본 그대로 사용한다.
    private List<ReviewResponseDTO> applyLimit(List<ReviewResponseDTO> reviews, int limit) {
        if (reviews.size() <= limit) {
            return reviews;
        }
        return List.copyOf(reviews.subList(0, limit));
    }
}
