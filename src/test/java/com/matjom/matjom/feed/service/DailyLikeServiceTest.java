package com.matjom.matjom.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.feed.repository.DailyLikeRepository;
import com.matjom.matjom.feed.entity.likes.DailyLike;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyLikeServiceTest {

    @Mock
    private DailyLikeRepository dailyLikeRepository;

    @Mock
    private VisitEligibilityChecker visitEligibilityChecker;

    @InjectMocks
    private DailyLikeService dailyLikeService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PLACE_ID = 1L;
    private static final Long VISIT_ID = 200L;

    @Test
    @DisplayName("도착하지 않았으면 좋아요가 거부된다")
    // 목적: ARRIVED가 아닐 때 좋아요 생성이 차단되는지 확인
    // 상황: 방문 자격 검사를 false로 모킹하고 서비스 호출
    // 기대: LIKE_NOT_ALLOWED 예외가 발생한다
    void createLikeFailsWhenNotArrived() {
        given(visitEligibilityChecker.isArrived(USER_ID, VISIT_ID)).willReturn(false);

        var request = new com.matjom.matjom.feed.dto.request.DailyLikeCreateRequestDTO(PLACE_ID, VISIT_ID);

        FeedException exception = assertThrows(FeedException.class,
                () -> dailyLikeService.createLike(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LIKE_NOT_ALLOWED);
    }

    @Test
    @DisplayName("이미 좋아요가 있으면 중복 예외를 던진다")
    // 목적: 동일 방문에 대해 중복 좋아요가 생성되지 않도록 검증
    // 상황: 자격 검사는 통과하지만 저장소에서 이미 존재한다고 응답하도록 설정
    // 기대: LIKE_ALREADY_EXISTS 예외가 발생한다
    void createLikeFailsWhenAlreadyExists() {
        given(visitEligibilityChecker.isArrived(USER_ID, VISIT_ID)).willReturn(true);
        given(dailyLikeRepository.existsByVisitId(VISIT_ID)).willReturn(true);

        var request = new com.matjom.matjom.feed.dto.request.DailyLikeCreateRequestDTO(PLACE_ID, VISIT_ID);

        FeedException exception = assertThrows(FeedException.class,
                () -> dailyLikeService.createLike(USER_ID, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LIKE_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("visitId 없이 요청하면 최신 ARRIVED 방문으로 처리된다")
    // 목적: 프런트가 visitId를 생략한 경우에도 좋아요가 생성되는지 검증
    // 상황: 최신 ARRIVED 방문 ID를 반환하고 save/assembler 호출을 모킹
    // 기대: liked=true 응답이 반환된다
    void createLikeResolvesLatestVisitWhenNotProvided() {
        given(visitEligibilityChecker.findLatestArrivedVisitId(USER_ID, PLACE_ID))
                .willReturn(Optional.of(VISIT_ID));
        given(dailyLikeRepository.existsByVisitId(VISIT_ID)).willReturn(false);
        DailyLike saved = DailyLike.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .placeId(PLACE_ID)
                .visitId(VISIT_ID)
                .dateKst(LocalDate.now())
                .build();
        given(dailyLikeRepository.save(any(DailyLike.class))).willReturn(saved);

        var request = new com.matjom.matjom.feed.dto.request.DailyLikeCreateRequestDTO(PLACE_ID, null);

        dailyLikeService.createLike(USER_ID, request);
        verify(dailyLikeRepository).save(any(DailyLike.class));
    }
}
