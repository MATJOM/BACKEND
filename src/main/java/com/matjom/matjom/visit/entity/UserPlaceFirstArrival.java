package com.matjom.matjom.visit.entity;

import com.matjom.matjom.common.entity.BaseEntity;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_place_first_arrivals")
public class UserPlaceFirstArrival extends BaseEntity {

    @EmbeddedId
    private UserPlaceFirstArrivalId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", columnDefinition = "uuid", nullable = false)
    private User user;

    @MapsId("placeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "first_arrived_at", nullable = false)
    private OffsetDateTime firstArrivedAt;

    protected UserPlaceFirstArrival() {
        // JPA
    }

    public UserPlaceFirstArrival(User user, Place place, OffsetDateTime firstArrivedAt) {
        this.id = new UserPlaceFirstArrivalId(user.getId(), place.getId());
        this.user = user;
        this.place = place;
        this.firstArrivedAt = firstArrivedAt;
    }

    public UserPlaceFirstArrivalId getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Place getPlace() {
        return place;
    }

    public OffsetDateTime getFirstArrivedAt() {
        return firstArrivedAt;
    }
}
