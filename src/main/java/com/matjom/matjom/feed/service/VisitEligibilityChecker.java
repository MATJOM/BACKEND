package com.matjom.matjom.feed.service;

import com.matjom.matjom.feed.repository.VisitReadRepository;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VisitEligibilityChecker {

    private final VisitReadRepository visitReadRepository;

    public VisitEligibilityStatus check(UUID userId, Long visitId) {
        return visitReadRepository.findByIdAndUserId(visitId, userId)
                .map(this::determineStatus)
                .orElse(VisitEligibilityStatus.NOT_FOUND); // 9월 26일 최종: 방문 자체가 없을 때 처리
    }

    private VisitEligibilityStatus determineStatus(Visit visit) {
        boolean arrived = visit.getArrivedAt() != null && visit.getState() == VisitState.ARRIVED;
        return arrived ? VisitEligibilityStatus.ARRIVED : VisitEligibilityStatus.NOT_ARRIVED; // 9월 26일 최종: 도착 여부만 판단
    }

    public enum VisitEligibilityStatus {
        ARRIVED, // 9월 26일 최종: 도착 확인 완료
        NOT_FOUND, // 9월 26일 최종: 방문 기록 없음
        NOT_ARRIVED // 9월 26일 최종: 아직 도착하지 않음
    }
}
