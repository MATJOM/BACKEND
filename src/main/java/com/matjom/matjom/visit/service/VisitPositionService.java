package com.matjom.matjom.visit.service;

import com.matjom.matjom.common.exception.base.SessionException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.visit.dto.VisitPositionRequest;
import com.matjom.matjom.visit.dto.VisitPositionResponse;
import com.matjom.matjom.visit.entity.ClientMode;
import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitPosition;
import com.matjom.matjom.visit.entity.VisitState;
import com.matjom.matjom.visit.entity.VisitStateEventSource;
import com.matjom.matjom.visit.geofence.GeoFenceEvaluationResult;
import com.matjom.matjom.visit.geofence.GeoFenceEvaluator;
import com.matjom.matjom.visit.repository.VisitPositionRepository;
import com.matjom.matjom.visit.repository.VisitRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitPositionService {

    private final VisitRepository visitRepository;
    private final VisitPositionRepository visitPositionRepository;
    private final GeoFenceEvaluator geoFenceEvaluator;
    private final VisitStateTransitionRecorder stateTransitionRecorder;

    public VisitPositionService(VisitRepository visitRepository,
                                VisitPositionRepository visitPositionRepository,
                                GeoFenceEvaluator geoFenceEvaluator,
                                VisitStateTransitionRecorder stateTransitionRecorder) {
        this.visitRepository = visitRepository;
        this.visitPositionRepository = visitPositionRepository;
        this.geoFenceEvaluator = geoFenceEvaluator;
        this.stateTransitionRecorder = Objects.requireNonNull(stateTransitionRecorder, "stateTransitionRecorder");
    }

    @Transactional
    public VisitPositionResponse recordPosition(Long sessionId, VisitPositionRequest request) {
		// 1) 세션(Visit) 조회
		Optional<Visit> optionalVisit = visitRepository.findById(sessionId);
        if (optionalVisit.isEmpty()) {
            throw new SessionException(ErrorCode.SESSION_NOT_FOUND); // 세션 없으면 예외
        }

		Visit visit = optionalVisit.get();
		// 2) 현재 세션 상태 확인
		VisitState previousState = visit.getState();
        if (visit.getState() != VisitState.ACTIVE) {
            throw new SessionException(ErrorCode.SESSION_ALREADY_INACTIVE); // 이미 비활성 상태면 기록 불가
        }

		// 3) 클라이언트 요청에서 위치 정보 꺼내기
		BigDecimal latitude = request.getLatitude();
        BigDecimal longitude = request.getLongitude();
        BigDecimal accuracy = request.getAccuracyMeters(); // GPS 정확도(m)
		ClientMode mode = request.modeOrDefault(); // 위치 수집 모드(AUTO/MANUAL 등)
        OffsetDateTime recordedAt = request.getRecordedAt(); // 위치 기록 시각

		// 4) 위치 엔티티 생성 (방문과 연결)
        VisitPosition position = new VisitPosition(visit, latitude, longitude, accuracy, mode, recordedAt);

		// 5) 지오펜스 평가 실행 (도착/체류시간/정확도 체크)
        GeoFenceEvaluationResult evaluation = geoFenceEvaluator.evaluate(visit, position);

		// 6) 위치 저장
        VisitPosition saved = visitPositionRepository.save(position);

		// 7) Visit 객체의 최신 위치 정보 업데이트
        visit.updateLastPosition(latitude, longitude, accuracy, recordedAt);

		// 8) 지오펜스 평가 결과에 따라 Visit 상태 전환 필요 여부 확인
		VisitState targetState = evaluation.getResultingState();
        if (targetState != null && targetState != visit.getState()) {
            visit.transitionTo(targetState);
        }

		// 9) 응답에 담을 상태/체류시간/정확도 플래그 추출
        VisitState responseState = visit.getState();
        long dwellSeconds = evaluation.getDwellSeconds();
        boolean accuracyPaused = evaluation.isAccuracyPaused();

		// 10) 상태가 변했다면 이벤트 기록(이력 남기기)
        VisitState currentState = visit.getState();
        if (currentState != previousState) {
            VisitStateEventSource source = VisitStateEventSource.SYSTEM;
            if (currentState == VisitState.ARRIVED) {
                source = VisitStateEventSource.AUTO_ARRIVAL; // 자동 도착이면 구분 표시
            }
            stateTransitionRecorder.record(visit, previousState, currentState, source, recordedAt);
        }

		// 11) 클라이언트 응답 DTO 생성
        return new VisitPositionResponse(
                saved.getId(), // 방금 저장된 위치 ID
                visit.getId(), // 세션 ID
                responseState, // 세션 상태 (ACTIVE/ARRIVED 등)
                recordedAt, // 기록된 시각
                dwellSeconds, // 누적 체류 시간
                accuracyPaused // 이번 기록이 정확도 문제로 일시정지 되었는지 여부
        );
    }
}
