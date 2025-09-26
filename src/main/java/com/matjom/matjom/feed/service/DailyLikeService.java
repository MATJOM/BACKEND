package com.matjom.matjom.feed.service;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.DailyLikeCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.DailyLikeResponseDTO;
import com.matjom.matjom.feed.dto.response.EligibilityCheckResponseDTO;
import com.matjom.matjom.feed.entity.likes.DailyLike;
import com.matjom.matjom.feed.entity.likes.LikeStatus;
import com.matjom.matjom.feed.repository.DailyLikeRepository;
import com.matjom.matjom.feed.service.VisitEligibilityChecker.VisitEligibilityStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
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

    public EligibilityCheckResponseDTO checkLikeEligibility(UUID userId, Long placeId, Long visitId) {
        log.info("좋아요 자격 확인: userId={}, placeId={}, visitId={}", userId, placeId, visitId);

        VisitEligibilityStatus visitStatus = visitEligibilityChecker.check(userId, visitId); // 9월 26일 최종: 방문 존재/도착 여부 확인

        if (visitStatus == VisitEligibilityStatus.NOT_FOUND) {
            return EligibilityCheckResponseDTO.notEligible("방문 기록을 찾을 수 없습니다", visitId,
                    false, false, false, false); // 9월 26일 최종: 방문 없음
        }

        if (visitStatus == VisitEligibilityStatus.NOT_ARRIVED) {
            return EligibilityCheckResponseDTO.notEligible("도착 확인 후 이용 가능합니다", visitId,
                    true, false, false, true); // 9월 26일 최종: 미도착
        }

        if (dailyLikeRepository.existsByVisitId(visitId)) {
            return EligibilityCheckResponseDTO.notEligible("이미 좋아요를 누르셨습니다", visitId,
                    true, true, true, true); // 9월 26일 최종: 중복 방지
        }

        return EligibilityCheckResponseDTO.eligible(visitId); // 9월 26일 최종: 모든 조건 통과
    }

    @Transactional
    public DailyLikeResponseDTO createLike(UUID userId, DailyLikeCreateRequestDTO request) {
        log.info("좋아요 등록 요청: userId={}, placeId={}, visitId={}", userId, request.getPlaceId(), request.getVisitId());

        EligibilityCheckResponseDTO eligibility = checkLikeEligibility(userId, request.getPlaceId(), request.getVisitId());
        if (!eligibility.getEligible()) {
            if (!Boolean.TRUE.equals(eligibility.getVisitExists())) {
                throw new FeedException(ErrorCode.LIKE_NOT_ALLOWED, eligibility.getReason()); // 9월 26일 최종: 방문 없음
            }
            if (!Boolean.TRUE.equals(eligibility.getVisitArrived())) {
                throw new FeedException(ErrorCode.ARRIVAL_NOT_CONFIRMED, eligibility.getReason()); // 9월 26일 최종: 미도착
            }
            if (Boolean.TRUE.equals(eligibility.getAlreadyWritten())) {
                throw new FeedException(ErrorCode.LIKE_ALREADY_EXISTS, eligibility.getReason()); // 9월 26일 최종: 중복 좋아요
            }
            throw new FeedException(ErrorCode.LIKE_NOT_ALLOWED, eligibility.getReason());
        }

        DailyLike dailyLike = DailyLike.builder()
                .userId(userId)
                .placeId(request.getPlaceId())
                .visitId(request.getVisitId())
                .dateKst(LocalDate.now(KST))
                .build(); // 9월 26일 최종: 최소 필드만 설정

        DailyLike saved = dailyLikeRepository.save(dailyLike);
        log.info("좋아요 등록 완료: likeId={}", saved.getId());
        return DailyLikeResponseDTO.from(saved);
    }

    @Transactional
    public void cancelLike(UUID userId, UUID likeId) {
        DailyLike dailyLike = dailyLikeRepository.findByIdAndUserId(likeId, userId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "좋아요를 찾을 수 없습니다.")); // 9월 26일 최종

        if (!dailyLike.isActive()) {
            throw new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "이미 취소된 좋아요입니다."); // 9월 26일 최종
        }

        dailyLike.cancel(OffsetDateTime.now());
        log.info("좋아요 취소 완료: likeId={}", likeId);
    }

    @Transactional
    public DailyLikeResponseDTO reactivateLike(UUID userId, UUID likeId) {
        DailyLike dailyLike = dailyLikeRepository.findByIdAndUserId(likeId, userId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "좋아요를 찾을 수 없습니다.")); // 9월 26일 최종

        if (dailyLike.isActive()) {
            throw new FeedException(ErrorCode.LIKE_ALREADY_EXISTS, "이미 활성화된 좋아요입니다."); // 9월 26일 최종
        }

        dailyLike.reactivate();
        log.info("좋아요 재활성화 완료: likeId={}", likeId);
        return DailyLikeResponseDTO.from(dailyLike);
    }

    public List<DailyLikeResponseDTO> getUserPlaceLikes(UUID userId, Long placeId) {
        return dailyLikeRepository
                .findByUserIdAndPlaceIdAndStatusOrderByCreatedAtDesc(userId, placeId, LikeStatus.ACTIVE)
                .stream()
                .map(DailyLikeResponseDTO::from)
                .toList(); // 9월 26일 최종: 활성 좋아요만 반환
    }
}
