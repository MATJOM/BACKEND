package com.matjom.matjom.feed.repository;

import com.matjom.matjom.feed.entity.likes.DailyLike;
import com.matjom.matjom.feed.entity.likes.LikeStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyLikeRepository extends JpaRepository<DailyLike, UUID> {

    boolean existsByVisitId(Long visitId); // 9월 26일 최종: 방문당 1건 유지

    Optional<DailyLike> findByIdAndUserId(UUID likeId, UUID userId); // 9월 26일 최종: 소유 확인

    List<DailyLike> findByUserIdAndPlaceIdAndStatusOrderByCreatedAtDesc(UUID userId,
                                                                        Long placeId,
                                                                        LikeStatus status); // 9월 26일 최종: 활성 좋아요 조회
}
