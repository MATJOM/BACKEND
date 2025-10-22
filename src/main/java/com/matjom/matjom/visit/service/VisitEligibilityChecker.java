package com.matjom.matjom.visit.service;

import com.matjom.matjom.visit.repository.VisitReadRepository;
import com.matjom.matjom.visit.entity.VisitState;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 리뷰/좋아요 등 후속 플로우에서 방문 자격을 검증하는 도우미 컴포넌트.
 * 사용 목적: ARRIVED 상태의 방문이 존재하는지 빠르게 확인하고, 최신 방문 ID나 도착 시각을 조회한다.
 * 코드 의미: 읽기 전용 리포지토리 쿼리를 감싸 호출부에서 중복된 조건식을 숨긴다.
 * 기대 결과: 자격 검증 로직이 여러 서비스로 흩어지지 않고 일관되게 재사용된다.
 */
@Component
@RequiredArgsConstructor
public class VisitEligibilityChecker {

    private final VisitReadRepository visitReadRepository;

    /**
     * 지정된 방문이 ARRIVED 상태인지 확인한다.
     */
    public boolean isArrived(UUID userId, Long visitId) {
        return visitReadRepository.existsByIdAndUserIdAndState(visitId, userId, VisitState.ARRIVED);
    }

    /**
     * 사용자의 최신 ARRIVED 방문 ID를 찾는다.
     * 사용 목적: 클라이언트가 visitId를 보내지 않아도 리뷰/좋아요를 자동 매칭한다.
     */
    public Optional<Long> findLatestArrivedVisitId(UUID userId, Long placeId) {
        return visitReadRepository.findLatestArrivedVisitId(userId, placeId);
    }

    /**
     * ARRIVED 방문의 도착 시각을 조회한다.
     * 사용 목적: 리뷰/좋아요 작성 가능 시간(24시간 제한 등)을 검증한다.
     */
    public Optional<OffsetDateTime> findArrivedAt(UUID userId, Long visitId) {
        return visitReadRepository.findArrivedAtByIdAndUserIdAndState(visitId, userId, VisitState.ARRIVED);
    }
}
