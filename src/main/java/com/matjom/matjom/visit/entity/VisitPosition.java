package com.matjom.matjom.visit.entity;

import com.matjom.matjom.common.entity.BaseEntity;
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

@Entity
@Table(name = "visit_positions", indexes = {
        @Index(name = "idx_visit_positions_visit", columnList = "visit_id"),
        @Index(name = "idx_visit_positions_visit_received", columnList = "visit_id, received_at")
})
public class VisitPosition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pos_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @Column(name = "lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "accuracy_m", precision = 6, scale = 2)
    private BigDecimal accuracyMeter;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private ClientMode mode;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    protected VisitPosition() {
        // JPA
    }

    public VisitPosition(Visit visit, BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeter, ClientMode mode, OffsetDateTime receivedAt) {
        this.visit = visit;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeter = accuracyMeter;
        this.mode = mode;
        this.receivedAt = receivedAt;
    }

    public Long getId() {
        return id;
    }

    public Visit getVisit() {
        return visit;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getAccuracyMeter() {
        return accuracyMeter;
    }

    public ClientMode getMode() {
        return mode;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }
}
