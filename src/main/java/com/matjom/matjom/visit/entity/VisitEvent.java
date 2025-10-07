package com.matjom.matjom.visit.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.matjom.matjom.common.entity.BaseEntity;
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
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "visit_events", indexes = {
        @Index(name = "idx_visit_events_visit", columnList = "visit_id, occurred_at")
})
public class VisitEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false)
    private Visit visit;

    @Column(name = "user_id", columnDefinition = "uuid", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private VisitEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", nullable = false, length = 20)
    private VisitState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 20)
    private VisitState toState;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
    private JsonNode meta;

    protected VisitEvent() {
        // JPA
    }

    public VisitEvent(Visit visit,
                      VisitEventType eventType,
                      VisitState fromState,
                      VisitState toState,
                      OffsetDateTime occurredAt,
                      JsonNode meta) {
        this.visit = Objects.requireNonNull(visit, "visit");
        User visitUser = visit.getUser();
        if (visitUser == null) {
            throw new IllegalArgumentException("visit user must not be null");
        }
        this.userId = visitUser.getId();
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.fromState = Objects.requireNonNull(fromState, "fromState");
        this.toState = Objects.requireNonNull(toState, "toState");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.meta = meta;
    }

    public Long getId() {
        return id;
    }

    public Visit getVisit() {
        return visit;
    }

    public UUID getUserId() {
        return userId;
    }

    public VisitEventType getEventType() {
        return eventType;
    }

    public VisitState getFromState() {
        return fromState;
    }

    public VisitState getToState() {
        return toState;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public JsonNode getMeta() {
        return meta;
    }
}
