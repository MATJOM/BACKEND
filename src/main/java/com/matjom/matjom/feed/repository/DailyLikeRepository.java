package com.matjom.matjom.feed.repository;

import com.matjom.matjom.feed.entity.likes.DailyLike;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyLikeRepository extends JpaRepository<DailyLike, UUID> {

    // 목적: 특정 방문에 좋아요가 이미 존재하는지 확인한다
    // 필요 이유: 방문당 한 번만 좋아요를 허용하기 위해서다
    // 로직: visitId 조건으로 존재 여부만 조회한다
    boolean existsByVisitId(Long visitId); // 9월 26일 최종: 방문당 1건 유지

    // 목적: 좋아요 식별자와 사용자 ID를 함께 확인한다
    // 필요 이유: 취소·재활성화 시 본인 소유인지 검증해야 한다
    // 로직: likeId와 userId를 모두 만족하는 레코드를 Optional로 반환한다
    Optional<DailyLike> findByIdAndUserId(UUID likeId, UUID userId); // 9월 26일 최종: 소유 확인
}
