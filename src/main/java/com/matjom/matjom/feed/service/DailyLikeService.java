package com.matjom.matjom.feed.service;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.DailyLikeCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.DailyLikeResponseDTO;
import com.matjom.matjom.feed.dto.response.EligibilityCheckResponseDTO;
import com.matjom.matjom.feed.entity.likes.DailyLike;
import com.matjom.matjom.feed.entity.likes.LikeStatus;
import com.matjom.matjom.feed.repository.DailyLikeRepository;
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
    private final DailyLikeResponseAssembler dailyLikeResponseAssembler;

    public EligibilityCheckResponseDTO checkLikeEligibility(UUID userId, Long placeId, Long visitId) {
        log.info("좋아요 자격 확인: userId={}, placeId={}, visitId={}", userId, placeId, visitId);

        VisitEligibilityChecker.VisitEligibilityStatus visitStatus = visitEligibilityChecker.check(userId, visitId);

        if (visitStatus == VisitEligibilityChecker.VisitEligibilityStatus.NOT_FOUND) {
            return EligibilityCheckResponseDTO.notEligible("방문 기록을 찾을 수 없습니다", visitId,
                    false, false, false, false);
        }

        if (visitStatus == VisitEligibilityChecker.VisitEligibilityStatus.NOT_ARRIVED) {
            return EligibilityCheckResponseDTO.notEligible("도착 확인 후 이용 가능합니다", visitId,
                    true, false, false, true);
        }

        if (dailyLikeRepository.existsByVisitId(visitId)) {
            return EligibilityCheckResponseDTO.notEligible("이미 좋아요를 누르셨습니다", visitId,
                    true, true, true, true);
        }

        return EligibilityCheckResponseDTO.eligible(visitId);
    }

    @Transactional
    public DailyLikeResponseDTO createLike(UUID userId, DailyLikeCreateRequestDTO request) {
        log.info("좋아요 등록 요청: userId={}, placeId={}, visitId={}", userId, request.getPlaceId(), request.getVisitId());

        EligibilityCheckResponseDTO eligibility = checkLikeEligibility(userId, request.getPlaceId(), request.getVisitId());
        if (!eligibility.getEligible()) {
            if (!Boolean.TRUE.equals(eligibility.getVisitExists())) {
                throw new FeedException(ErrorCode.LIKE_NOT_ALLOWED, eligibility.getReason());
            }
            if (!Boolean.TRUE.equals(eligibility.getVisitArrived())) {
                throw new FeedException(ErrorCode.ARRIVAL_NOT_CONFIRMED, eligibility.getReason());
            }
            if (Boolean.TRUE.equals(eligibility.getAlreadyWritten())) {
                throw new FeedException(ErrorCode.LIKE_ALREADY_EXISTS, eligibility.getReason());
            }
            throw new FeedException(ErrorCode.LIKE_NOT_ALLOWED, eligibility.getReason());
        }

        DailyLike dailyLike = DailyLike.builder()
                .userId(userId)
                .placeId(request.getPlaceId())
                .visitId(request.getVisitId())
                .dateKst(LocalDate.now(KST))
                .build();

        DailyLike saved = dailyLikeRepository.save(dailyLike);
        log.info("좋아요 등록 완료: likeId={}", saved.getId());
        return dailyLikeResponseAssembler.toDto(saved);
    }

    @Transactional
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
    public DailyLikeResponseDTO reactivateLike(UUID userId, UUID likeId) {
        DailyLike dailyLike = dailyLikeRepository.findByIdAndUserId(likeId, userId)
                .orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "좋아요를 찾을 수 없습니다."));

        if (dailyLike.isActive()) {
            throw new FeedException(ErrorCode.LIKE_ALREADY_EXISTS, "이미 활성화된 좋아요입니다.");
        }

        dailyLike.reactivate();
        log.info("좋아요 재활성화 완료: likeId={}", likeId);
        return dailyLikeResponseAssembler.toDto(dailyLike);
    }

    public List<DailyLikeResponseDTO> getUserPlaceLikes(UUID userId, Long placeId) {
        return dailyLikeRepository
                .findByUserIdAndPlaceIdAndStatusOrderByCreatedAtDesc(userId, placeId, LikeStatus.ACTIVE)
                .stream()
                .map(dailyLikeResponseAssembler::toDto)
                .toList();
    }
}
