package com.matjom.matjom.placefacade.dto;

import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.statistics.dto.PlaceStatsResponseDTO;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlaceDetailResponseDTO {

    private final PlaceStatsResponseDTO statistics; // 9월 30일 최종: 통계 패키지 응답 그대로 포함
    private final List<ReviewResponseDTO> reviews;   // 9월 30일 최종: 검증된 리뷰 응답 리스트
    private final long totalReviewCount;            // 9월 30일 최종: 전체 리뷰 수(제한 전 기준)
}
