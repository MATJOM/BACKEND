package com.matjom.matjom.feed.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;

import com.matjom.matjom.feed.dto.response.EligibilityCheckResponseDTO;
import com.matjom.matjom.feed.repository.DailyLikeRepository;
import com.matjom.matjom.feed.service.DailyLikeResponseAssembler;
import com.matjom.matjom.feed.service.VisitEligibilityChecker.VisitEligibilityStatus;
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

    @Mock
    private DailyLikeResponseAssembler dailyLikeResponseAssembler;

    @InjectMocks
    private DailyLikeService dailyLikeService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long PLACE_ID = 1L;
    private static final Long VISIT_ID = 200L;

    @Test
    @DisplayName("방문 도착 상태이고 중복이 없으면 좋아요 가능")
    void checkLikeEligibilityReturnsEligibleWhenArrived() {
        given(visitEligibilityChecker.check(USER_ID, VISIT_ID)).willReturn(VisitEligibilityStatus.ARRIVED);
        given(dailyLikeRepository.existsByVisitId(VISIT_ID)).willReturn(false);

        EligibilityCheckResponseDTO response = dailyLikeService.checkLikeEligibility(USER_ID, PLACE_ID, VISIT_ID);

        assertAll(
                () -> assertThat(response.getEligible()).isTrue(),
                () -> assertThat(response.getVisitExists()).isTrue(),
                () -> assertThat(response.getVisitArrived()).isTrue()
        );
    }

    @Test
    @DisplayName("아직 도착하지 않은 방문이면 좋아요 불가")
    void checkLikeEligibilityReturnsNotEligibleWhenNotArrived() {
        given(visitEligibilityChecker.check(USER_ID, VISIT_ID)).willReturn(VisitEligibilityStatus.NOT_ARRIVED);

        EligibilityCheckResponseDTO response = dailyLikeService.checkLikeEligibility(USER_ID, PLACE_ID, VISIT_ID);

        assertAll(
                () -> assertThat(response.getEligible()).isFalse(),
                () -> assertThat(response.getVisitExists()).isTrue(),
                () -> assertThat(response.getVisitArrived()).isFalse()
        );
    }
}
