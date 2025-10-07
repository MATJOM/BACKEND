# 4.2 위치 수신 API 구현 요약

## 1. 주요 컴포넌트
- `VisitSessionController.recordPosition(...)`
  - `POST /api/v1/sessions/{sessionId}/positions` 엔드포인트 추가.
  - 요청 DTO 검증 후 `VisitPositionService` 호출, `ApiResponse` 포맷으로 응답.
- `VisitPositionService.recordPosition(...)`
  - 세션 조회 → ACTIVE 상태 검증 → `VisitPosition` 엔터티 생성.
  - `DefaultGeoFenceEvaluator` 호출로 dwell, accuracyPaused, 상태 판단.
  - 위치 이벤트 저장 후 `Visit.updateLastPosition`으로 최근 좌표/정확도/시각 갱신.
  - 필요 시 `Visit.transitionTo(...)`로 상태 전이(평가 결과가 ARRIVED로 바뀌었을 때).
  - `VisitPositionResponse`에 positionId, sessionId, state, recordedAt, dwell, accuracyPaused 반환.
- `VisitPositionRequest / VisitPositionResponse`
  - 위도/경도/정확도/recordedAt/모드 필드를 보유. validation annotation으로 범위 체크.
- `VisitPositionRepository`
  - Spring Data JPA로 위치 이벤트 저장.
- `GeoFenceEvaluator` 인터페이스 & `GeoFenceEvaluationResult`
  - 4.3에서 실제 로직을 구현할 수 있도록 계약 정의.
  - 현재 `NoopGeoFenceEvaluator`는 기본 상태(Active)와 dwell 0을 반환.

## 2. 상태/데이터 흐름
1. 클라이언트가 위치 데이터를 전송.
2. 서비스가 세션을 찾아 ACTIVE인지 확인.
3. 위치 이벤트를 `visit_positions` 테이블에 INSERT.
4. 세션 엔터티의 `last_position_at`, `last_lat`, `last_lng`, `last_accuracy_m` 업데이트.
5. GeoFenceEvaluator가 결과를 반환하면(추후 ARRIVED 등) 세션 상태를 변경할 수 있다.
6. 응답엔 최신 상태와 dwell 값(현재 0), accuracyPaused(현재 false)를 내려준다.

## 3. 테스트
- `VisitPositionServiceTest`
  - 정상 저장, 세션 미존재, 이미 종료된 세션, evaluator가 ARRIVED 반환 시 상태 전이 확인.
- `./gradlew test` 전체 수행으로 회귀 검증.

## 4. TODO / 후속 작업
- GeoFenceEvaluator 실제 구현(4.3)에서 dwell 계산·ARRIVED 전이를 반영.
- 정확도 가드(4.4) 적용 시 `accuracyPaused` 값을 evaluator에서 제어.
- OpenAPI 스펙 업데이트(`/api/v1/sessions/{id}/positions`) 및 예외 코드 문서화.
