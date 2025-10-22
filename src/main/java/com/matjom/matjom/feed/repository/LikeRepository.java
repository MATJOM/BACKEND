package com.matjom.matjom.feed.repository;

import com.matjom.matjom.feed.entity.likes.Like;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 좋아요 엔티티에 대한 JPA 리포지토리.
 * 사용 목적: 방문·사용자 기준으로 좋아요를 조회하고 존재 여부를 확인한다.
 * 코드 의미: Spring Data 파생 메서드로 단일/다중 조건 조회와 존재 검사 로직을 선언한다.
 * 기대 결과: 서비스가 중복 없이 좋아요 상태를 읽고 변경할 수 있다.
 */
public interface LikeRepository extends JpaRepository<Like, UUID> {

    /**
     * 방문 ID 기준으로 좋아요 존재 여부를 확인한다.
     * 사용 목적: 방문당 좋아요 1건 제한을 검증한다.
     * 코드 의미: exists 쿼리로 빠르게 true/false를 판별한다.
     * 기대 결과: 중복 좋아요 저장을 차단한다.
     */
    boolean existsByVisitId(Long visitId);

    /**
     * 방문 ID로 좋아요 엔티티를 조회한다.
     * 사용 목적: 기존 좋아요 상태를 확인하거나 취소/재활성화 시 재사용한다.
     * 코드 의미: visitId 파생 질의를 사용해 Optional로 결과를 반환한다.
     * 기대 결과: 필요 시 엔티티를 불러와 상태 전환을 수행할 수 있다.
     */
    Optional<Like> findByVisitId(Long visitId);

    /**
     * 좋아요 ID와 사용자 ID로 엔티티를 조회한다.
     * 사용 목적: 현재 사용자 소유의 좋아요인지 검증한다.
     * 코드 의미: 복합 조건 파생 메서드로 불일치 시 Optional.empty()를 반환한다.
     * 기대 결과: 권한이 없는 사용자의 취소 요청을 차단한다.
     */
    Optional<Like> findByIdAndUserId(UUID likeId, UUID userId);

    /**
     * 방문 ID 목록으로 활성 좋아요를 조회한다.
     * 사용 목적: 여러 방문 건에 대한 좋아요 상태를 일괄 확인한다.
     * 코드 의미: deletedAt이 NULL인 레코드만 가져와 소프트 삭제 데이터를 제외한다.
     * 기대 결과: 현재 유효한 좋아요 목록만 반환된다.
     */
    List<Like> findByVisitIdInAndDeletedAtIsNull(Collection<Long> visitIds);
}
