package com.matjom.matjom.visit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class UserPlaceFirstArrivalId implements Serializable {

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "place_id")
    private Long placeId;

    protected UserPlaceFirstArrivalId() {
    }

    public UserPlaceFirstArrivalId(UUID userId, Long placeId) {
        this.userId = userId;
        this.placeId = placeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Long getPlaceId() {
        return placeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPlaceFirstArrivalId that = (UserPlaceFirstArrivalId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(placeId, that.placeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, placeId);
    }
}
