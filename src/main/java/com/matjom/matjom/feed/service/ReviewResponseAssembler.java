package com.matjom.matjom.feed.service;

import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.PlaceReadRepository;
import com.matjom.matjom.feed.repository.UserReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewResponseAssembler {

    private static final String UNKNOWN = "알 수 없음"; // 9월 26일 최종: 기본 문자열

    private final UserReadRepository userReadRepository;
    private final PlaceReadRepository placeReadRepository;

    public ReviewResponseDTO toDto(Review review) {
        String reviewerName = loadReviewerName(review.getUserId());
        String placeName = loadPlaceName(review.getPlaceId());
        return ReviewResponseDTO.of(review, reviewerName, placeName);
    }

    private String loadReviewerName(java.util.UUID userId) {
        try {
            return userReadRepository.findNameById(userId).orElse(UNKNOWN);
        } catch (DataAccessException ex) {
            log.debug("사용자 이름 조회 실패: userId={}", userId, ex);
            return UNKNOWN;
        }
    }

    private String loadPlaceName(Long placeId) {
        try {
            return placeReadRepository.findNameById(placeId).orElse(UNKNOWN);
        } catch (DataAccessException ex) {
            log.debug("장소 이름 조회 실패: placeId={} ", placeId, ex);
            return UNKNOWN;
        }
    }
}
