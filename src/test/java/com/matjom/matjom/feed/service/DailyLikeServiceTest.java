package com.matjom.matjom.feed.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.DailyLikeCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.DailyLikeResponseDTO;
import com.matjom.matjom.feed.dto.response.EligibilityCheckResponseDTO;
import com.matjom.matjom.feed.entity.likes.DailyLike;
import com.matjom.matjom.feed.entity.likes.LikeStatus;
import com.matjom.matjom.feed.repository.DailyLikeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class) // 수정제안 2024-09-24: Mockito 확장 사용으로 목 초기화 자동화.
class DailyLikeServiceTest {

    @Mock
    private DailyLikeRepository dailyLikeRepository;

    private DailyLikeService dailyLikeService;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul"); // 수정제안 2024-09-24: 중복 사용 방지용 상수.
    private final LocalDate expectedDate = LocalDate.of(2024, 9, 24); // 수정제안 2024-09-24: 테스트 재현성 확보.
    private Clock fixedClock;                                      // 수정제안 2024-09-24: 고정된 Clock.

    private UUID userId;
    private DailyLikeCreateRequestDTO request;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(expectedDate.atStartOfDay(KST).toInstant(), KST); // 수정제안 2024-09-24
        dailyLikeService = new DailyLikeService(dailyLikeRepository, fixedClock);  // 수정제안 2024-09-24

        userId = UUID.randomUUID();                                               // 수정제안 2024-09-24
        request = new DailyLikeCreateRequestDTO(1L, 1000L);                       // 수정제안 2024-09-24
    }

    @Nested
    @DisplayName("createLike")
    class CreateLike {

        @Test
        void 방문당_첫_좋아요는_정상적으로_저장된다() {
            when(dailyLikeRepository.existsByVisitId(request.getVisitId())).thenReturn(false);
            when(dailyLikeRepository.save(any(DailyLike.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0, DailyLike.class));

            DailyLikeResponseDTO response = dailyLikeService.createLike(userId, request);

            verify(dailyLikeRepository).save(any(DailyLike.class));
            assertThat(response.getPlaceId()).isEqualTo(request.getPlaceId());
            assertThat(response.getVisitId()).isEqualTo(request.getVisitId());
            assertThat(response.getStatus()).isEqualTo(LikeStatus.ACTIVE);
            assertThat(response.getDateKst()).isEqualTo(expectedDate); // 수정제안 2024-09-24
        }

        @Test
        void 동일_방문에_재요청하면_중복_예외를_던진다() {
            when(dailyLikeRepository.existsByVisitId(request.getVisitId())).thenReturn(true);

            assertThatThrownBy(() -> dailyLikeService.createLike(userId, request))
                    .isInstanceOf(FeedException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.LIKE_ALREADY_EXISTS);

            verify(dailyLikeRepository, never()).save(any(DailyLike.class));
        }
    }

    @Nested
    @DisplayName("cancelLike")
    class CancelLike {

        @Test
        void 본인이_누른_좋아요를_취소하면_상태가_변경된다() {
            DailyLike like = DailyLike.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .placeId(request.getPlaceId())
                    .visitId(request.getVisitId())
                    .dateKst(expectedDate)                  // 수정제안 2024-09-24
                    .status(LikeStatus.ACTIVE)
                    .build();

            when(dailyLikeRepository.findById(like.getId())).thenReturn(Optional.of(like));

            dailyLikeService.cancelLike(userId, like.getId());

            assertThat(like.getStatus()).isEqualTo(LikeStatus.CANCELLED);
            assertThat(like.getCancelledAt()).isNotNull();
        }

        @Test
        void 좋아요가_없으면_NOT_FOUND를_던진다() {
            UUID likeId = UUID.randomUUID();
            when(dailyLikeRepository.findById(likeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dailyLikeService.cancelLike(userId, likeId))
                    .isInstanceOf(FeedException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.LIKE_NOT_FOUND);
        }

        @Test
        void 다른_사용자가_누른_좋아요이면_FORBIDDEN을_던진다() {
            DailyLike like = DailyLike.builder()
                    .id(UUID.randomUUID())
                    .userId(UUID.randomUUID())
                    .placeId(request.getPlaceId())
                    .visitId(request.getVisitId())
                    .dateKst(expectedDate)                  // 수정제안 2024-09-24
                    .status(LikeStatus.ACTIVE)
                    .build();

            when(dailyLikeRepository.findById(like.getId())).thenReturn(Optional.of(like));

            assertThatThrownBy(() -> dailyLikeService.cancelLike(userId, like.getId()))
                    .isInstanceOf(FeedException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("getUserPlaceLikes")
    class GetUserPlaceLikes {

        @Test
        void 활성_좋아요만_반환한다() {
            DailyLike firstVisit = DailyLike.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .placeId(1L)
                    .visitId(500L)
                    .dateKst(expectedDate)
                    .status(LikeStatus.ACTIVE)
                    .build();

            DailyLike secondVisit = DailyLike.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .placeId(1L)
                    .visitId(501L)
                    .dateKst(expectedDate)
                    .status(LikeStatus.ACTIVE)
                    .build();
            secondVisit.cancel();                            // 수정제안 2024-09-24

            when(dailyLikeRepository.findByUserIdAndPlaceId(userId, 1L))
                    .thenReturn(List.of(firstVisit, secondVisit));

            List<DailyLikeResponseDTO> result = dailyLikeService.getUserPlaceLikes(userId, 1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getVisitId()).isEqualTo(500L);
        }
    }
    @Nested
    @DisplayName("reactivateLike")
    class ReactivateLike {

        @Test
        void 취소된_좋아요를_재등록하면_ACTIVE로_변경된다() {
            DailyLike like = DailyLike.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .placeId(request.getPlaceId())
                    .visitId(request.getVisitId())
                    .dateKst(expectedDate)
                    .status(LikeStatus.ACTIVE)
                    .build();
            like.cancel(); // 수정제안 2024-09-24: CANCELLED 상태를 미리 만든다.

            when(dailyLikeRepository.findById(like.getId())).thenReturn(Optional.of(like));

            DailyLikeResponseDTO response = dailyLikeService.reactivateLike(userId, like.getId());

            assertThat(response.getStatus()).isEqualTo(LikeStatus.ACTIVE);
            assertThat(response.getCancelledAt()).isNull();
        }

        @Test
        void 이미_ACTIVE이면_LIKE_ALREADY_EXISTS를_던진다() {
            DailyLike like = DailyLike.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .placeId(request.getPlaceId())
                    .visitId(request.getVisitId())
                    .dateKst(expectedDate)
                    .status(LikeStatus.ACTIVE)
                    .build();

            when(dailyLikeRepository.findById(like.getId())).thenReturn(Optional.of(like));

            assertThatThrownBy(() -> dailyLikeService.reactivateLike(userId, like.getId()))
                    .isInstanceOf(FeedException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.LIKE_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("cancelLike 추가 케이스")
    class CancelLikeExtra {

        @Test
        void 이미_취소된_좋아요는_LIKE_NOT_ALLOWED를_던진다() {
            DailyLike like = DailyLike.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .placeId(request.getPlaceId())
                    .visitId(request.getVisitId())
                    .dateKst(expectedDate)
                    .status(LikeStatus.ACTIVE)
                    .build();
            like.cancel(); // 수정제안 2024-09-24

            when(dailyLikeRepository.findById(like.getId())).thenReturn(Optional.of(like));

            assertThatThrownBy(() -> dailyLikeService.cancelLike(userId, like.getId()))
                    .isInstanceOf(FeedException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.LIKE_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("getUserPlaceLikes 추가 케이스")
    class GetUserPlaceLikesExtra {

        @Test
        void 좋아요가_없으면_빈_리스트를_반환한다() {
            when(dailyLikeRepository.findByUserIdAndPlaceId(userId, 1L))
                    .thenReturn(List.of()); // 수정제안 2024-09-24

            List<DailyLikeResponseDTO> result = dailyLikeService.getUserPlaceLikes(userId, 1L);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("checkLikeEligibility")
    class CheckLikeEligibility {

        @Test
        void 중복이_없으면_eligible_true() {
            when(dailyLikeRepository.existsByVisitId(request.getVisitId())).thenReturn(false);

            EligibilityCheckResponseDTO dto =
                    dailyLikeService.checkLikeEligibility(userId, request.getPlaceId(), request.getVisitId());

            assertThat(dto.getEligible()).isTrue();
            assertThat(dto.getAlreadyWritten()).isFalse();
        }

        @Test
        void 동일_visit이_있으면_eligible_false() {
            when(dailyLikeRepository.existsByVisitId(request.getVisitId())).thenReturn(true);

            EligibilityCheckResponseDTO dto =
                    dailyLikeService.checkLikeEligibility(userId, request.getPlaceId(), request.getVisitId());

            assertThat(dto.getEligible()).isFalse();
            assertThat(dto.getAlreadyWritten()).isTrue();
            assertThat(dto.getReason()).contains("이미 좋아요");
        }
    }


}