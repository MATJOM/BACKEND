package com.matjom.matjom.feed.repository;

import com.matjom.matjom.feed.entity.likes.DailyLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyLikeRepository extends JpaRepository<DailyLike, UUID> {

    // 방문 기반 조회 (중복 체크용)
    Optional<DailyLike> findByVisitId(Long visitId);
    boolean existsByVisitId(Long visitId);

    // 사용자가 특정 장소에 누른 좋아요들 (이력 확인용)
    @Query("SELECT dl FROM DailyLike dl WHERE dl.userId = :userId AND dl.placeId = :placeId AND dl.status = 'ACTIVE' AND dl.deletedAt IS NULL ORDER BY dl.createdAt DESC")
    List<DailyLike> findByUserIdAndPlaceId(@Param("userId") UUID userId, @Param("placeId") Long placeId);
}
