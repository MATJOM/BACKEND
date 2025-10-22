package com.matjom.matjom.feed.dto.response;

import com.matjom.matjom.feed.entity.review.Review;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리뷰 조회 응답을 표현하는 DTO.
 * 사용 목적: 리뷰 엔터티에서 노출 가능한 정보만 추려 API 응답으로 직렬화한다.
 * 코드 의미: 빌더 패턴을 활용해 엔터티 -> DTO 변환 시 기본값 처리와 필드 매핑을 수행한다.
 * 기대 결과: 클라이언트가 리뷰 작성자, 본문, 작성 시각을 신뢰할 수 있는 형식으로 받는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponseDTO {
    private static final String UNKNOWN_REVIEWER = "알 수 없음";

    private String reviewerName;
    private String text;
    private OffsetDateTime createdAt;

    /**
     * 리뷰 엔터티를 응답 DTO로 변환한다.
     * 사용 목적: 컨트롤러/서비스가 중복 로직 없이 통일된 출력 포맷을 제공한다.
     * 코드 의미: 작성자 이름이 비어 있으면 기본 텍스트로 채우고, 필요 필드를 복사한다.
     * 기대 결과: Null 값이 제거된 안전한 리뷰 응답 DTO를 생성한다.
     */
    public static ReviewResponseDTO of(Review review) {
        String reviewerName = review.getUserName();
        if (reviewerName == null || reviewerName.isBlank()) {
            reviewerName = UNKNOWN_REVIEWER;
        }
        return ReviewResponseDTO.builder()
                .reviewerName(reviewerName)
                .text(review.getText())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
