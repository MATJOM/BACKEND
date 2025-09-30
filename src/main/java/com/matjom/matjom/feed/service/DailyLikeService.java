package com.matjom.matjom.feed.service;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.DailyLikeCreateRequestDTO;
import com.matjom.matjom.feed.entity.likes.DailyLike;
import com.matjom.matjom.feed.entity.likes.LikeStatus;
import com.matjom.matjom.feed.repository.DailyLikeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DailyLikeService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyLikeRepository dailyLikeRepository;
    private final VisitEligibilityChecker visitEligibilityChecker;
    @Transactional
    // 목적: 방문 완료 사용자의 일일 좋아요를 생성한다
    // 필요 이유: 방문 경험에 대한 긍정 평가를 누적 통계로 활용하기 위함이다
    // 로직: ARRIVED 여부와 중복 여부를 검사한 뒤 레코드를 저장한다
    public void createLike(UUID userId, DailyLikeCreateRequestDTO request) {
        log.info("좋아요 등록 요청: userId={}, placeId={}, visitId={}", userId, request.getPlaceId(), request.getVisitId());

        Long visitId = resolveArrivedVisitIdForLike(userId, request.getPlaceId(), request.getVisitId()); // 9월 30일 최종: visitId 자동 매칭

        if (dailyLikeRepository.existsByVisitId(visitId)) {
            throw new FeedException(ErrorCode.LIKE_ALREADY_EXISTS, "이미 좋아요를 누르셨습니다");
        }

        DailyLike dailyLike = DailyLike.builder()
                .userId(userId)
                .placeId(request.getPlaceId())
                .visitId(visitId)
                .dateKst(LocalDate.now(KST))
                .build();

        DailyLike saved = dailyLikeRepository.save(dailyLike);
        log.info("좋아요 등록 완료: likeId={}", saved.getId());
    }

    @Transactional
    // 목적: 사용자가 누른 좋아요를 비활성화한다
    // 필요 이유: 의사 변경 시 기록은 남기면서 집계에서는 제외해야 한다
    // 로직: 사용자 소유의 좋아요인지 확인 후 현재 시간을 기준으로 cancel 처리한다
    public void cancelLike(UUID userId, UUID likeId) {
        DailyLike dailyLike = dailyLikeRepository.findByIdAndUserId(likeId, userId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "좋아요를 찾을 수 없습니다."));

        if (!dailyLike.isActive()) {
            throw new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "이미 취소된 좋아요입니다.");
        }

        dailyLike.cancel(OffsetDateTime.now());
        log.info("좋아요 취소 완료: likeId={}", likeId);
    }

    @Transactional
    // 목적: 취소한 좋아요를 다시 활성화한다
    // 필요 이유: 동일 방문에 대한 재평가가 가능하도록 UX를 보완한다
    // 로직: 소유 여부와 현재 상태를 검증한 후 엔티티의 reactivate를 호출해 DTO로 변환한다
    public void reactivateLike(UUID userId, UUID likeId) {
        DailyLike dailyLike = dailyLikeRepository.findByIdAndUserId(likeId, userId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "좋아요를 찾을 수 없습니다."));

        if (dailyLike.isActive()) {
            throw new FeedException(ErrorCode.LIKE_ALREADY_EXISTS, "이미 활성화된 좋아요입니다.");
        }

        dailyLike.reactivate();
        log.info("좋아요 재활성화 완료: likeId={}", likeId);
    }

    // 목적: 좋아요 요청에 사용할 ARRIVED 방문 ID를 결정한다
    // 필요 이유: 프런트에서 visitId를 생략해도 최신 도착 방문에 대해 좋아요를 누를 수 있도록 한다
    // 로직: 명시된 visitId는 ARRIVED 여부를 검증하고, 없으면 최신 ARRIVED 방문 ID를 조회한다
    private Long resolveArrivedVisitIdForLike(UUID userId, Long placeId, Long requestedVisitId) {
        if (requestedVisitId != null) {
            if (!visitEligibilityChecker.isArrived(userId, requestedVisitId)) { // 9월 30일 최종: ARRIVED 확인
                throw new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "도착한 방문이 없습니다");
            }
            return requestedVisitId;
        }

        return visitEligibilityChecker.findLatestArrivedVisitId(userId, placeId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "도착한 방문이 없습니다"));
    }
}
