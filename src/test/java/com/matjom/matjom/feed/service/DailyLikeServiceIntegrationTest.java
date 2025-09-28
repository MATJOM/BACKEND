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
    // 목적: 통합 환경에서 ARRIVED 조건 충족 시 좋아요가 저장되고 DTO가 반환되는지 검증
    // 상황: 방문 자격을 true로 모킹하고 서비스 호출 후 저장소에서 상태를 확인
    // 기대: 좋아요가 ACTIVE 상태로 저장되고 응답에 기본 이름이 포함된다
    void createLikePersistsWhenEligible() {
        given(visitEligibilityChecker.isArrived(USER_ID, VISIT_ID)).willReturn(true);
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
    // 목적: 취소 API가 저장된 레코드의 상태를 CANCELLED로 바꾸는지 확인
    // 상황: 좋아요를 생성한 뒤 cancelLike를 호출
    // 기대: 저장소에서 조회한 상태 값이 CANCELLED로 바뀐다
    void cancelLikeSetsStatusCancelled() {
        given(visitEligibilityChecker.isArrived(USER_ID, VISIT_ID)).willReturn(true);
        DailyLikeCreateRequestDTO request = new DailyLikeCreateRequestDTO(PLACE_ID, VISIT_ID);
        DailyLikeResponseDTO created = dailyLikeService.createLike(USER_ID, request);

        dailyLikeService.cancelLike(USER_ID, created.getLikeId());

        DailyLike cancelled = dailyLikeRepository.findById(created.getLikeId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(LikeStatus.CANCELLED);
    }

    @Test
    @DisplayName("좋아요 재활성화 시 ACTIVE로 변경")
    // 목적: 재활성화 API가 비활성화된 좋아요를 다시 ACTIVE로 전환하는지 검증
    // 상황: 생성 후 cancelLike/ reactivateLike 순서로 호출
    // 기대: 최종적으로 저장된 상태가 ACTIVE가 된다
    void reactivateLikeSetsStatusActive() {
        given(visitEligibilityChecker.isArrived(USER_ID, VISIT_ID)).willReturn(true);
        DailyLikeCreateRequestDTO request = new DailyLikeCreateRequestDTO(PLACE_ID, VISIT_ID);
        DailyLikeResponseDTO created = dailyLikeService.createLike(USER_ID, request);

        dailyLikeService.cancelLike(USER_ID, created.getLikeId());
        dailyLikeService.reactivateLike(USER_ID, created.getLikeId());

        DailyLike reactivated = dailyLikeRepository.findById(created.getLikeId()).orElseThrow();
        assertThat(reactivated.getStatus()).isEqualTo(LikeStatus.ACTIVE);
    }

    @Test
    @DisplayName("방문 정보가 없으면 LIKE_NOT_ALLOWED 예외")
    // 목적: 자격 조건 미충족 시 예외 흐름을 확인
    // 상황: 방문 자격 검증에서 false를 반환하도록 설정
    // 기대: LIKE_NOT_ALLOWED 예외가 발생한다
    void createLikeFailsWhenVisitMissing() {
        given(visitEligibilityChecker.isArrived(USER_ID, VISIT_ID)).willReturn(false);
        DailyLikeCreateRequestDTO request = new DailyLikeCreateRequestDTO(PLACE_ID, VISIT_ID);

        FeedException exception = assertThrows(FeedException.class, () -> dailyLikeService.createLike(USER_ID, request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LIKE_NOT_ALLOWED);
    }

}
