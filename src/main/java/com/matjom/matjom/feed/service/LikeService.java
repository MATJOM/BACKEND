package com.matjom.matjom.feed.service;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.LikeCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.LikeStatusResponseDTO;
import com.matjom.matjom.feed.entity.likes.Like;
import com.matjom.matjom.feed.repository.LikeRepository;
import com.matjom.matjom.visit.service.VisitEligibilityChecker;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 좋아요 생성/취소/재활성화를 담당하는 서비스.
 * 사용 목적: 방문 검증과 시간 제한을 적용해 좋아요 상태를 관리한다.
 * 코드 의미: 방문 검증기와 리포지토리를 조합해 트랜잭션 단위로 상태 전환을 수행한다.
 * 기대 결과: 규칙에 맞는 요청만 반영되어 데이터 무결성을 유지한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LikeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LikeRepository likeRepository;
    private final VisitEligibilityChecker visitEligibilityChecker;

    /**
     * 좋아요를 신규 등록한다.
     * 사용 목적: 방문 후 24시간 내에 첫 좋아요를 기록한다.
     * 코드 의미: 방문 ID 확정 → 중복 확인 → 엔티티 생성/저장 흐름을 수행한다.
     * 기대 결과: 성공 시 활성 상태와 식별자를 담은 응답을 반환한다.
     */
    @Transactional
    public LikeStatusResponseDTO createLike(UUID userId, LikeCreateRequestDTO request) {
        log.info("좋아요 등록 요청: userId={}, placeId={}, visitId={}", userId, request.getPlaceId(), request.getVisitId());

        Long visitId = resolveArrivedVisitIdForLike(userId, request.getPlaceId(), request.getVisitId());

        if (likeRepository.existsByVisitId(visitId)) {
            throw new FeedException(ErrorCode.LIKE_ALREADY_EXISTS, "이미 좋아요를 누르셨습니다");
        }

        Like like = Like.builder()
                .userId(userId)
                .placeId(request.getPlaceId())
                .visitId(visitId)
                .dateKst(LocalDate.now(KST))
                .build();

        Like saved = likeRepository.save(like);
        log.info("좋아요 등록 완료: likeId={}", saved.getId());
        return LikeStatusResponseDTO.builder()
                .liked(true)
                .likeId(saved.getId())
                .build();
    }

    /**
     * 기존 좋아요를 취소한다.
     * 사용 목적: 사용자가 더 이상 좋아요를 유지하지 않을 때 상태를 변경한다.
     * 코드 의미: 사용자 소유 여부 확인 → 시간 제한 검증 → 상태 전환 순으로 처리한다.
     * 기대 결과: 성공 시 취소된 상태 정보를 반환한다.
     */
    @Transactional
    public LikeStatusResponseDTO cancelLike(UUID userId, UUID likeId) {
        Like like = likeRepository.findByIdAndUserId(likeId, userId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "좋아요를 찾을 수 없습니다."));

        ensureWithinWindow(userId, like.getVisitId());

        if (!like.isActive()) {
            throw new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "이미 취소된 좋아요입니다.");
        }

        like.cancel(OffsetDateTime.now());
        log.info("좋아요 취소 완료: likeId={}", likeId);
        return LikeStatusResponseDTO.builder()
                .liked(false)
                .likeId(likeId)
                .build();
    }

    /**
     * 취소된 좋아요를 재활성화한다.
     * 사용 목적: 동일 방문에 대해 좋아요를 다시 유효화한다.
     * 코드 의미: 사용자 검증과 시간 제한 확인 후 상태를 ACTIVE로 되돌린다.
     * 기대 결과: 성공 시 활성화된 좋아요 정보를 반환한다.
     */
    @Transactional
    public LikeStatusResponseDTO reactivateLike(UUID userId, UUID likeId) {
        Like like = likeRepository.findByIdAndUserId(likeId, userId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "좋아요를 찾을 수 없습니다."));

        ensureWithinWindow(userId, like.getVisitId());

        if (like.isActive()) {
            throw new FeedException(ErrorCode.LIKE_ALREADY_EXISTS, "이미 활성화된 좋아요입니다.");
        }

        like.reactivate();
        log.info("좋아요 재활성화 완료: likeId={}", likeId);
        return LikeStatusResponseDTO.builder()
                .liked(true)
                .likeId(likeId)
                .build();
    }

    /**
     * 좋아요 처리에 사용할 방문 ID를 결정한다.
     * 사용 목적: 요청에 방문 ID가 없으면 최근 방문을 찾아 사용한다.
     * 코드 의미: 방문 검증기에서 도착 여부를 확인하고 시간 제한도 함께 검증한다.
     * 기대 결과: 유효한 방문 ID를 반환하거나 조건 미충족 시 예외를 던진다.
     */
    private Long resolveArrivedVisitIdForLike(UUID userId, Long placeId, Long requestedVisitId) {
        if (requestedVisitId != null) {
            ensureWithinWindow(userId, requestedVisitId);
            return requestedVisitId;
        }

        Long visitId = visitEligibilityChecker.findLatestArrivedVisitId(userId, placeId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "도착한 방문이 없습니다"));
        ensureWithinWindow(userId, visitId);
        return visitId;
    }

    /**
     * 방문 후 24시간 이내인지 검증한다.
     * 사용 목적: 좋아요 생성/취소/재활성화가 허용된 시간인지 확인한다.
     * 코드 의미: 방문 도착 시각을 조회하고 현재 시간과 비교한다.
     * 기대 결과: 제한을 넘으면 예외가 발생해 요청이 거절된다.
     */
    private void ensureWithinWindow(UUID userId, Long visitId) {
        OffsetDateTime arrivedAt = visitEligibilityChecker.findArrivedAt(userId, visitId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "도착한 방문이 없습니다"));
        if (arrivedAt.plusHours(24).isBefore(OffsetDateTime.now())) {
            throw new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "방문 후 24시간이 지나 좋아요를 변경할 수 없습니다");
        }
    }
}
