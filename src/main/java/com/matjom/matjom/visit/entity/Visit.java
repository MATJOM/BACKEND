package com.matjom.matjom.visit.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.matjom.matjom.common.entity.BaseEntity;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "visits", indexes = {
        @Index(name = "idx_visits_user", columnList = "user_id"),
        @Index(name = "idx_visits_place", columnList = "place_id"),
        @Index(name = "idx_visits_state", columnList = "state"),
        @Index(name = "idx_visits_started_at", columnList = "started_at")
})
public class Visit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "visit_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", columnDefinition = "uuid", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private VisitState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_mode", nullable = false, length = 20)
    private ClientMode clientMode;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "arrived_at")
    private OffsetDateTime arrivedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "expired_at")
    private OffsetDateTime expiredAt;

    @Column(name = "last_pos_at")
    private OffsetDateTime lastPositionAt;

    @Column(name = "dwell_started_at")
    private OffsetDateTime dwellStartedAt;

    @Column(name = "last_lat", precision = 9, scale = 6)
    private BigDecimal lastLatitude;

    @Column(name = "last_lng", precision = 9, scale = 6)
    private BigDecimal lastLongitude;

    @Column(name = "last_accuracy_m", precision = 6, scale = 2)
    private BigDecimal lastAccuracyMeter;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
    private JsonNode meta;

    protected Visit() {
        // JPA
    }

    public Visit(User user,
                 Place place,
                 ClientMode clientMode,
                 OffsetDateTime startedAt) {
        this.user = Objects.requireNonNull(user, "user");
        this.place = Objects.requireNonNull(place, "place");
        this.clientMode = clientMode == null ? ClientMode.NAVIGATION : clientMode;
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        this.state = VisitState.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Place getPlace() {
        return place;
    }

    public VisitState getState() {
        return state;
    }

    public ClientMode getClientMode() {
        return clientMode;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getArrivedAt() {
        return arrivedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public OffsetDateTime getExpiredAt() {
        return expiredAt;
    }

    public OffsetDateTime getLastPositionAt() {
        return lastPositionAt;
    }

    public OffsetDateTime getDwellStartedAt() {
        return dwellStartedAt;
    }

    public BigDecimal getLastLatitude() {
        return lastLatitude;
    }

    public BigDecimal getLastLongitude() {
        return lastLongitude;
    }

    public BigDecimal getLastAccuracyMeter() {
        return lastAccuracyMeter;
    }

    public JsonNode getMeta() {
        return meta;
    }

}
