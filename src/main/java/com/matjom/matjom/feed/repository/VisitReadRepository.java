package com.matjom.matjom.feed.repository;

import com.matjom.matjom.visit.entity.Visit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitReadRepository extends JpaRepository<Visit, Long> {

    @Query("""
        SELECT v FROM Visit v
        WHERE v.id = :visitId
          AND v.user.id = :userId
          AND v.deletedAt IS NULL
    """)
    Optional<Visit> findByIdAndUserId(@Param("visitId") Long visitId,
                                      @Param("userId") UUID userId); // 9월 26일 최종: 리뷰/좋아요 자격 검증용 단순 조회
}
