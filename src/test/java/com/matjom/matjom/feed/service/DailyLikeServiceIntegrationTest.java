package com.matjom.matjom.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.dto.request.DailyLikeCreateRequestDTO;
import com.matjom.matjom.feed.dto.response.DailyLikeResponseDTO;
import com.matjom.matjom.feed.entity.likes.DailyLike;
import com.matjom.matjom.feed.entity.likes.LikeStatus;
import com.matjom.matjom.feed.repository.DailyLikeRepository;
import com.matjom.matjom.feed.service.VisitEligibilityChecker.VisitEligibilityStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DailyLikeServiceIntegrationTest {

    @Autowired
    private DailyLikeService dailyLikeService;

    @Autowired
    private DailyLikeRepository dailyLikeRepository;

    @MockBean
    private VisitEligibilityChecker visitEligibilityChecker;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PLACE_ID = 2L;
    private static final Long VISIT_ID = 20L;

    @Test
    @DisplayName("좋아요 등록 후 저장 및 조회")
    void createLikePersistsWhenEligible() {
        given(visitEligibilityChecker.check(USER_ID, VISIT_ID)).willReturn(VisitEligibilityStatus.ARRIVED);
        DailyLikeCreateRequestDTO request = new DailyLikeCreateRequestDTO(PLACE_ID, VISIT_ID);

        DailyLikeResponseDTO response = dailyLikeService.createLike(USER_ID, request);

        DailyLike saved = dailyLikeRepository.findById(response.getLikeId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(LikeStatus.ACTIVE);
        assertThat(saved.getPlaceId()).isEqualTo(PLACE_ID);
        assertThat(response.getUserName()).isEqualTo("알 수 없음");
        assertThat(response.getPlaceName()).isEqualTo("알 수 없음");
    }

    @Test
    @DisplayName("좋아요 취소 후 상태가 CANCELLED로 변경")
    void cancelLikeSetsStatusCancelled() {
        given(visitEligibilityChecker.check(USER_ID, VISIT_ID)).willReturn(VisitEligibilityStatus.ARRIVED);
        DailyLikeCreateRequestDTO request = new DailyLikeCreateRequestDTO(PLACE_ID, VISIT_ID);
        DailyLikeResponseDTO created = dailyLikeService.createLike(USER_ID, request);

        dailyLikeService.cancelLike(USER_ID, created.getLikeId());

        DailyLike cancelled = dailyLikeRepository.findById(created.getLikeId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(LikeStatus.CANCELLED);
    }

    @Test
    @DisplayName("좋아요 재활성화 시 ACTIVE로 변경")
    void reactivateLikeSetsStatusActive() {
        given(visitEligibilityChecker.check(USER_ID, VISIT_ID)).willReturn(VisitEligibilityStatus.ARRIVED);
        DailyLikeCreateRequestDTO request = new DailyLikeCreateRequestDTO(PLACE_ID, VISIT_ID);
        DailyLikeResponseDTO created = dailyLikeService.createLike(USER_ID, request);

        dailyLikeService.cancelLike(USER_ID, created.getLikeId());
        dailyLikeService.reactivateLike(USER_ID, created.getLikeId());

        DailyLike reactivated = dailyLikeRepository.findById(created.getLikeId()).orElseThrow();
        assertThat(reactivated.getStatus()).isEqualTo(LikeStatus.ACTIVE);
    }

    @Test
    @DisplayName("방문 정보가 없으면 LIKE_NOT_ALLOWED 예외")
    void createLikeFailsWhenVisitMissing() {
        given(visitEligibilityChecker.check(USER_ID, VISIT_ID)).willReturn(VisitEligibilityStatus.NOT_FOUND);
        DailyLikeCreateRequestDTO request = new DailyLikeCreateRequestDTO(PLACE_ID, VISIT_ID);

        FeedException exception = assertThrows(FeedException.class, () -> dailyLikeService.createLike(USER_ID, request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LIKE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("사용자/장소별 활성 좋아요 조회")
    void getUserPlaceLikesReturnsActiveOnes() {
        given(visitEligibilityChecker.check(USER_ID, VISIT_ID)).willReturn(VisitEligibilityStatus.ARRIVED);
        DailyLikeCreateRequestDTO request = new DailyLikeCreateRequestDTO(PLACE_ID, VISIT_ID);
        DailyLikeResponseDTO created = dailyLikeService.createLike(USER_ID, request);

        List<DailyLikeResponseDTO> likes = dailyLikeService.getUserPlaceLikes(USER_ID, PLACE_ID);
        assertThat(likes)
                .hasSize(1)
                .first()
                .extracting(DailyLikeResponseDTO::getUserName, DailyLikeResponseDTO::getPlaceName)
                .containsExactly("알 수 없음", "알 수 없음");
    }
}
