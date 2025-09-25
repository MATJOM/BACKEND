package com.matjom.matjom.feed.entity.review;

import com.matjom.matjom.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@ToString(exclude = {"userId"}) // 민감한 정보 제외
public class Review extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "visit_id", nullable = false, unique = true)
    private Long visitId;

    @Column(name = "text", nullable = false, length = 140)
    @Setter
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Setter
    @Builder.Default
    private ReviewStatus status = ReviewStatus.ACTIVE;

    @Column(name = "flagged", nullable = false)
    @Setter
    @Builder.Default
    private Boolean flagged = false;

        // 비즈니스 메서드
    public void hide() {
        this.status = ReviewStatus.HIDDEN;
    }

    public void delete() {
        this.status = ReviewStatus.DELETED;
        this.markDeleted();
    }

    public void flag() {
        this.flagged = true;
    }

    public boolean isActive() {
        return status == ReviewStatus.ACTIVE && !isDeleted();
    }

    // 편의 생성자
    public Review(UUID userId, Long placeId, Long visitId, String text) {
        this.userId = userId;
        this.placeId = placeId;
        this.visitId = visitId;
        this.text = text;
        this.status = ReviewStatus.ACTIVE;
        this.flagged = false;
    }
}
