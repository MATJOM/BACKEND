package com.matjom.matjom.place.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Optional;
import org.springframework.util.StringUtils;

public class PlaceSearchRequest {

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

    @Positive(message = "size는 1 이상이어야 합니다.")
    @Max(value = 500, message = "size는 최대 500까지 허용됩니다.")
    private Integer size;

    @Pattern(regexp = "^[A-Za-z0-9.,:_-]*$", message = "cursor 형식이 올바르지 않습니다.")
    private String cursor;

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

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    public String getFilters() {
        return filters;
    }

    public void setFilters(String filters) {
        this.filters = filters;
    }

    public double radiusOrDefault(double defaultValue) {
        return radius != null ? radius : defaultValue;
    }

    public int sizeOrDefault(int defaultValue) {
        return size != null ? size : defaultValue;
    }

    public Optional<PlaceSearchCursor> parseCursor() {
        if (!StringUtils.hasText(cursor)) {
            return Optional.empty();
        }
        return Optional.ofNullable(PlaceSearchCursor.from(cursor));
    }
}
