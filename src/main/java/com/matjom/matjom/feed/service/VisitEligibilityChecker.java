package com.matjom.matjom.feed.service;

import com.matjom.matjom.feed.repository.VisitReadRepository;
import com.matjom.matjom.visit.entity.VisitState;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VisitEligibilityChecker {

    private final VisitReadRepository visitReadRepository;

    // 목적: 리뷰/좋아요 요청이 방문 완료 상태인지 신속히 판정한다
    // 필요 이유: ARRIVED 조건을 중앙에서 재사용해 중복 코드를 줄이고 정책 일관성을 유지한다
    // 로직: 방문 ID와 사용자 ID로 ARRIVED 상태가 존재하는지 Boolean 쿼리를 실행한다
    public boolean isArrived(UUID userId, Long visitId) {
        return visitReadRepository.existsByIdAndUserIdAndState(visitId, userId, VisitState.ARRIVED); // 9월 30일 최종: 존재 여부만 조회하는 경량 검증
    }

    // 목적: 사용자의 최신 ARRIVED 방문 ID를 조회한다
    // 필요 이유: 프런트에서 visitId를 생략해도 자동 매칭이 가능하도록 하기 위함이다
    // 로직: 리포지토리의 도착 시각 내림차순 조회 결과에서 첫 번째 항목의 ID를 Optional로 감싼다
    public Optional<Long> findLatestArrivedVisitId(UUID userId, Long placeId) {
        return visitReadRepository.findLatestArrivedVisitId(userId, placeId); // 9월 30일 최종: ARRIVED 방문 자동 탐색
    }
}
