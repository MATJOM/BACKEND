package com.matjom.matjom.feed.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리뷰 수정 요청을 위한 DTO.
 * 사용 목적: 수정 대상 리뷰의 ID와 변경할 본문을 받아 서비스 레이어에 전달한다.
 * 코드 의미: 필수 입력값을 Bean Validation으로 검증해 빈 값이나 과도한 길이를 차단한다.
 * 기대 결과: 유효한 요청만 리뷰 수정 로직으로 흘러가 데이터 무결성을 보장한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewUpdateRequestDTO {
    @NotBlank(message = "리뷰 내용은 필수입니다")
    @Size(min = 1, max = 140, message = "리뷰는 1-140자 사이로 작성해주세요")
    private String text;
}
