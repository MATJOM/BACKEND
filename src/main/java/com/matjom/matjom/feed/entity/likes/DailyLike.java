package com.matjom.matjom.feed.entity.likes;

import com.matjom.matjom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DailyLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "visit_id", nullable = false, unique = true)
    private Long visitId;

    @Column(name = "date_kst", nullable = false)
    private LocalDate dateKst;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private LikeStatus status = LikeStatus.ACTIVE; // 9월 26일 최종: ACTIVE/CANCELLED만 유지

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    // 목적: 좋아요가 현재 활성 상태인지 판별한다
    // 필요 이유: 취소된 좋아요를 중복 처리하지 않기 위해서다
    // 로직: 상태가 ACTIVE이며 삭제되지 않은 경우에만 true를 반환한다
    public boolean isActive() { // 9월 26일 최종: 단순 활성 상태 판단
        return status == LikeStatus.ACTIVE && !isDeleted();
    }

    // 목적: 좋아요를 취소 상태로 전환한다
    // 필요 이유: 사용자가 좋아요를 철회했을 때 집계에서 제외하려면 상태 전환이 필요하다
    // 로직: 상태를 CANCELLED로 바꾸고 취소 시각을 기록한다
    public void cancel(OffsetDateTime cancelledAt) { // 9월 26일 최종: 취소 처리
        this.status = LikeStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    // 목적: 취소한 좋아요를 다시 활성화한다
    // 필요 이유: 사용자가 마음을 바꿨을 때 같은 방문으로 재사용할 수 있어야 한다
    // 로직: 상태를 ACTIVE로 돌리고 취소 시각을 비운다
    public void reactivate() { // 9월 26일 최종: 재활성화 처리
        this.status = LikeStatus.ACTIVE;
        this.cancelledAt = null;
    }
}
