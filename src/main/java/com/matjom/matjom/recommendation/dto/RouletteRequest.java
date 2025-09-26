package com.matjom.matjom.recommendation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class RouletteRequest {

    @NotNull(message = "위도(lat)는 필수입니다.")
    @DecimalMin(value = "-90.0", inclusive = true, message = "위도(lat)는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", inclusive = true, message = "위도(lat)는 90 이하이어야 합니다.")
    private Double lat;

    @NotNull(message = "경도(lng)는 필수입니다.")
    @DecimalMin(value = "-180.0", inclusive = true, message = "경도(lng)는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", inclusive = true, message = "경도(lng)는 180 이하이어야 합니다.")
    private Double lng;

    @Positive(message = "반경(radius)은 양수여야 합니다.")
    private Double radius;

    @Size(max = 200, message = "filters는 200자 이하여야 합니다.")
    private String filters;

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public Double getRadius() {
        return radius;
    }

    public void setRadius(Double radius) {
        this.radius = radius;
    }

    public String getFilters() {
        return filters;
    }

    public void setFilters(String filters) {
        this.filters = filters;
    }
}
