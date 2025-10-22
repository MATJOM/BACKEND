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

/**
 * 일일 좋아요 상태를 나타내는 엔티티.
 * 사용 목적: 사용자-장소-방문 조합의 좋아요 생성/취소 이력을 영속화한다.
 * 코드 의미: 상태/취소 시각 등을 컬럼으로 정의해 소프트 삭제와 상태 전환을 지원한다.
 * 기대 결과: 하루에 하나의 visit 기준으로 좋아요 상태를 정확히 추적할 수 있다.
 */
@Entity
@Table(name = "daily_likes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Like extends BaseEntity {

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
    private LikeStatus status = LikeStatus.ACTIVE;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    /**
     * 좋아요가 현재 활성화되어 있는지 확인한다.
     * 사용 목적: API 응답이나 비즈니스 로직에서 노출 가능한 상태인지 판단한다.
     * 코드 의미: Enum 상태가 ACTIVE이고, 소프트 삭제 플래그가 내려가 있지 않은 경우만 true로 본다.
     * 기대 결과: 활성/비활성 판단이 한 곳에 모여 재사용된다.
     */
    public boolean isActive() {
        return status == LikeStatus.ACTIVE && !isDeleted();
    }

    /**
     * 좋아요를 취소 상태로 전환한다.
     * 사용 목적: 사용자가 좋아요를 취소할 때 상태와 취소 시각을 기록한다.
     * 코드 의미: Enum을 CANCELLED로 바꾸고, 전달받은 시간을 `cancelledAt`에 저장한다.
     * 기대 결과: 취소된 좋아요는 다시 활성화되기 전까지 조회에서 제외된다.
     */
    public void cancel(OffsetDateTime cancelledAt) {
        this.status = LikeStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    /**
     * 취소된 좋아요를 다시 활성화한다.
     * 사용 목적: 동일 방문에 대한 좋아요를 복구하거나 재사용할 때 활용한다.
     * 코드 의미: 상태를 ACTIVE로 되돌리고 취소 시각을 초기화한다.
     * 기대 결과: 취소된 상태가 복원되어 누락 없이 다시 조회된다.
     */
    public void reactivate() {
        this.status = LikeStatus.ACTIVE;
        this.cancelledAt = null;
    }
}
