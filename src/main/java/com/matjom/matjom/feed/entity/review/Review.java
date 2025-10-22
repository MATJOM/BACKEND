package com.matjom.matjom.feed.entity.review;

import com.matjom.matjom.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * 리뷰 도메인 엔티티.
 * 사용 목적: 사용자 리뷰의 작성자/본문/방문 정보를 영속화하고 관리한다.
 * 코드 의미: JPA 매핑을 통해 UUID 식별자와 연관 필드를 DB 컬럼에 연결한다.
 * 기대 결과: 리뷰 생성·수정·조회 시 일관된 스키마로 데이터베이스와 동기화된다.
 */

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "visit_id", nullable = false, unique = true)
    private Long visitId;

    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    @Column(name = "text", nullable = false, length = 140)
    @Setter
    private String text;

    /**
     * 리뷰가 활성 상태인지 확인한다.
     * 사용 목적: 소프트 삭제를 사용하는 환경에서 표시 가능 여부를 빠르게 판단한다.
     * 코드 의미: BaseEntity의 `isDeleted()` 결과를 반대로 반환해 삭제 여부를 확인한다.
     * 기대 결과: `true`면 노출 가능, `false`면 숨김 처리 상태임을 즉시 알 수 있다.
     */
    public boolean isActive() {
        return !isDeleted();
    }
}
