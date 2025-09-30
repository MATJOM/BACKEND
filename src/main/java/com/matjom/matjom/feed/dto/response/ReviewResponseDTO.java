package com.matjom.matjom.feed.dto.response;

import com.matjom.matjom.feed.entity.review.Review;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponseDTO {
    private String reviewerName; // 9월 30일 최종: 프런트 표시용 작성자 이름
    private String text;         // 9월 30일 최종: 리뷰 본문
    private OffsetDateTime createdAt; // 9월 30일 최종: 작성 시각 표시 용도

    // 목적: 리뷰 엔티티와 조회한 이름 정보를 조합해 DTO를 생성한다
    // 필요 이유: 서비스 계층에서 builder 로직을 반복하지 않고 일관된 응답을 만들기 위함이다
    // 로직: 엔티티 필드와 전달받은 이름을 builder에 채워 DTO를 완성한다
    public static ReviewResponseDTO of(Review review, String reviewerName) {
        return ReviewResponseDTO.builder()
                .reviewerName(reviewerName)
                .text(review.getText())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
