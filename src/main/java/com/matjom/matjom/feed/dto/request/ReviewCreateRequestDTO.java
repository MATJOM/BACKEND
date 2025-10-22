package com.matjom.matjom.feed.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리뷰 작성 요청 본문을 나타내는 DTO.
 * 사용 목적: 클라이언트가 보낸 장소/방문/본문 정보를 검증하고 서비스 계층으로 전달한다.
 * 코드 의미: 필수/선택 필드를 Bean Validation으로 정의해 입력 형태를 강제한다.
 * 기대 결과: 검증을 통과한 요청만 리뷰 생성 로직에 전달되어 일관된 데이터를 저장한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCreateRequestDTO {
    @NotNull(message = "장소 ID는 필수입니다")
    private Long placeId;

    private Long visitId;

    @NotBlank(message = "리뷰 내용은 필수입니다")
    @Size(min = 1, max = 140, message = "리뷰는 1-140자 사이로 작성해주세요")
    private String text;
}
