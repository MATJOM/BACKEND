package com.matjom.matjom.feed.entity.likes;

import com.matjom.matjom.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "daily_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@ToString(exclude = {"userId"}) // 민감한 정보 제외
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
    @Setter
    @Builder.Default
    private LikeStatus status = LikeStatus.ACTIVE;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;


    // 비즈니스 메서드
    // 수정사항 2024-09-24: 취소 처리 시 상태/취소 시각을 함께 갱신합니다.
    public void cancel() {
        if (status == LikeStatus.CANCELLED) {
            return;
        }
        this.status = LikeStatus.CANCELLED;
        this.cancelledAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    // 수정사항 2024-09-24: 재활성화 시 취소 흔적을 초기화합니다.
    public void reactivate() {
        this.status = LikeStatus.ACTIVE;
        this.cancelledAt = null;
    }


    public boolean isActive() {
        return status == LikeStatus.ACTIVE;
    }

    // 편의 생성자
    public DailyLike(UUID userId, Long placeId, Long visitId, LocalDate dateKst) {
        this.userId = userId;
        this.placeId = placeId;
        this.visitId = visitId;
        this.dateKst = dateKst;
        this.status = LikeStatus.ACTIVE;
    }

}
