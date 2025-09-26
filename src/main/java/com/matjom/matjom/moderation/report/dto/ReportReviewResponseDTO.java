package com.matjom.matjom.moderation.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.matjom.matjom.feed.entity.review.ReviewStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.matjom.matjom.moderation.report.entity.ReportReason;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportReviewResponseDTO {

    // 수정제안 2024-09-26: 신고된 리뷰와 신고 자체의 식별자를 함께 반환합니다.
    private final UUID reviewId;
    private final UUID reportId;

    // 수정제안 2024-09-26: 신고자를 추적하기 위해 reporterId를 포함합니다.
    private final UUID reporterId;

    // 수정제안 2024-09-26: 신고 사유와 상세 설명을 응답에 포함합니다.
    private final ReportReason reason;
    private final String description;

    // 수정제안 2024-09-26: 신고로 인해 리뷰 상태가 어떻게 바뀌었는지 전달합니다.
    private final ReviewStatus reviewStatus;

    // 수정제안 2024-09-26: 신고 시점을 ISO-8601 포맷으로 반환합니다.
    private final OffsetDateTime reportedAt;

    // 9월26일 수정제안: 누적 경고 수를 노출합니다.
    private final int warningCount;
}
