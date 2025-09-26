package com.matjom.matjom.feed.entity.review;

import com.matjom.matjom.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
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

    // 9월26일 수정제안: 경고 누적 개수를 보관합니다.
    @Column(name = "warning_count", nullable = false)
    @Builder.Default
    private int warningCount = 0;

    // 9월26일 수정제안: 마지막 경고 발급 시각을 추적합니다.
    @Column(name = "last_warning_at")
    private OffsetDateTime lastWarningAt;

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

    // 9월26일 수정제안: 자동/수동 경고 시 공통으로 호출합니다.
    public void recordWarning(OffsetDateTime issuedAt) {
        this.warningCount += 1;
        this.lastWarningAt = issuedAt;
        this.flagged = true;
    }

    // 9월26일 수정제안: 관리자가 경고를 해제할 때 사용합니다.
    public void resetWarnings() {
        this.warningCount = 0;
        this.lastWarningAt = null;
        this.flagged = false;
        if (this.status == ReviewStatus.HIDDEN && !isDeleted()) {
            this.status = ReviewStatus.ACTIVE;
        }
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
