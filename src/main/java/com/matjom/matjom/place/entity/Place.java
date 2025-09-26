package com.matjom.matjom.place.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.matjom.matjom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

@SuppressWarnings("unused")
@Entity
@Table(name = "places", indexes = {
        @Index(name = "idx_places_provider_id", columnList = "provider_id"),
        @Index(name = "idx_places_addr_sido", columnList = "addr_sido"),
        @Index(name = "idx_places_addr_sigungu", columnList = "addr_sigungu"),
        @Index(name = "idx_places_addr_eupmyeondong", columnList = "addr_eupmyeondong")
})
public class Place extends BaseEntity {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "place_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "category", columnDefinition = "text[]", nullable = false)
    private String[] category;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "working_hours", columnDefinition = "jsonb", nullable = false)
    private JsonNode workingHours;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "break_time", columnDefinition = "jsonb", nullable = false)
    private JsonNode breakTime;

    @Column(name = "opened_at", nullable = false)
    private LocalDate openedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opened_at_source", columnDefinition = "jsonb", nullable = false)
    private JsonNode openedAtSource;

    @Column(name = "biz_status", nullable = false, length = 20)
    private String bizStatus;

    @Column(name = "addr_sido", nullable = false, length = 20)
    private String addrSido;

    @Column(name = "addr_sigungu", nullable = false, length = 30)
    private String addrSigungu;

    @Column(name = "addr_eupmyeondong", nullable = false, length = 80)
    private String addrEupmyeondong;

    @Column(name = "addr_street", nullable = false, length = 100)
    private String addrStreet;

    @Column(name = "addr_detail", nullable = false, length = 100)
    private String addrDetail;

    @Column(name = "location", columnDefinition = "geography(Point,4326)")
    private Point location;

    protected Place() {
        // JPA
    }

    @PrePersist
    @PreUpdate
    void updateLocation() {
        if (latitude != null && longitude != null) {
            this.location = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude.doubleValue(), latitude.doubleValue()));
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public List<String> getCategory() {
        return category == null ? List.of() : List.copyOf(Arrays.asList(category.clone()));
    }

    public void setCategory(List<String> category) {
        this.category = category == null ? null : category.toArray(new String[0]);
    }

    public String getProviderId() {
        return providerId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public JsonNode getWorkingHours() {
        return workingHours;
    }

    public JsonNode getBreakTime() {
        return breakTime;
    }

    public LocalDate getOpenedAt() {
        return openedAt;
    }

    public JsonNode getOpenedAtSource() {
        return openedAtSource;
    }

    public String getBizStatus() {
        return bizStatus;
    }

    public String getAddrSido() {
        return addrSido;
    }

    public String getAddrSigungu() {
        return addrSigungu;
    }

    public String getAddrEupmyeondong() {
        return addrEupmyeondong;
    }

    public String getAddrStreet() {
        return addrStreet;
    }

    public String getAddrDetail() {
        return addrDetail;
    }

    public Point getLocation() {
        return location;
    }
}
