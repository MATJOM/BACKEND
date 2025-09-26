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
    private Double radius;

    @Size(max = 5, message = "categories는 최대 5개까지 허용됩니다.")
    private List<@Size(min = 1, max = 30, message = "카테고리는 1~30자여야 합니다.") String> categories;

    @Positive(message = "limit은 1 이상이어야 합니다.")
    private Integer limit;

    private Long seed;

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
