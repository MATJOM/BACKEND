package com.matjom.matjom.feed.repository;

import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitReadRepository extends JpaRepository<Visit, Long> {

    @Query("""
        SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
        FROM Visit v
        WHERE v.id = :visitId
          AND v.user.id = :userId
          AND v.state = :state
          AND v.deletedAt IS NULL
    """)
    // 목적: ARRIVED 상태 방문이 실제 존재하는지 확인한다
    // 필요 이유: 리뷰/좋아요 자격 검증에서 전체 엔티티 조회 없이 빠르게 판정하기 위함이다
    // 로직: visitId·userId·state 조건을 모두 충족하는 행의 존재 여부를 Boolean으로 반환한다
    boolean existsByIdAndUserIdAndState(@Param("visitId") Long visitId,
                                        @Param("userId") UUID userId,
                                        @Param("state") VisitState state); // 9월 30일 최종: ARRIVED 여부만 판정하는 경량 쿼리
}
