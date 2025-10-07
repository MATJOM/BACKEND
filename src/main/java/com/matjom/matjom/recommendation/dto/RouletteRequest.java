package com.matjom.matjom.recommendation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/*
* RouletteRequest는 기본값 헬퍼(radiusOrDefault, limitOrDefault)로 null 처리 부담을 서비스에서 제거하고,
* 카테고리 배열은 없으면 null을 반환해 SQL에서 :categories IS NULL 분기를 타도록 설계되었습니다.
* seed는 QA나 리플레이 상황에서 동일 결과를 만들기 위한 도구이며, 미지정 시 서버가 균등 난수로 선택합니다.
* */
public class RouletteRequest {
    @NotNull(message = "위도(lat)는 필수입니다.")
    @DecimalMin(value = "-90.0", message = "위도(lat)는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "위도(lat)는 90 이하이어야 합니다.")
    private Double lat;

    @NotNull(message = "경도(lng)는 필수입니다.")
    @DecimalMin(value = "-180.0", message = "경도(lng)는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "경도(lng)는 180 이하이어야 합니다.")
    private Double lng;

    @Positive(message = "반경(radius)은 양수여야 합니다.")
    private Double radius; // 검색 반경(미터). null이면 기본 300m

    @Size(max = 5, message = "categories는 최대 5개까지 허용됩니다.")
    private List<@Size(min = 1, max = 30, message = "카테고리는 1~30자여야 합니다.") String> categories; // 카테고리 교집합 필터

    @Positive(message = "limit은 1 이상이어야 합니다.")
    private Integer limit; // 후보 최대 개수. null → 기본 200, 상한 500

    private Long seed; // 동일 seed로 재현 가능한 추천을 만들기 위한 옵션

	public double radiusOrDefault(double defaultValue) {
        return radius != null ? radius : defaultValue;
    }

    public int limitOrDefault(int defaultValue) {
        return limit != null ? limit : defaultValue;
    }

    public List<String> categoriesOrNull() {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        return categories;
    }
}
