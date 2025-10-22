package com.matjom.matjom.moderation.report.dto;

import com.matjom.matjom.moderation.report.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 리뷰 신고 요청 본문을 표현하는 DTO.
 * 사용 목적: 신고 사유와 선택 설명을 검증해 서비스 계층에 전달한다.
 * 코드 의미: 필수/선택 필드에 Bean Validation을 부여해 입력을 제한한다.
 * 기대 결과: 유효한 신고 데이터만 Moderation 서비스로 전달된다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ReportReviewRequestDTO {

    /**
     * 신고 사유 (필수).
     * 사용 목적: 허용된 Enum 범위 내에서 신고 유형을 지정한다.
     * 기대 결과: Null일 경우 검증 단계에서 거부된다.
     */
    @NotNull(message = "신고 사유(reason)는 필수입니다.")
    private ReportReason reason;

    /**
     * 신고 세부 설명 (선택).
     * 사용 목적: 신고자가 추가 맥락을 전달하도록 한다.
     * 기대 결과: 최대 500자를 초과하면 검증 예외가 발생한다.
     */
    @Size(max = 500, message = "세부 설명(description)은 500자를 넘을 수 없습니다.")
    private String description;
}
