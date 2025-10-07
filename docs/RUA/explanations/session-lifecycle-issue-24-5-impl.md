# 4.5 수동 도착 확정 구현 요약

## 구현 개요
- `POST /api/v1/sessions/{sessionId}/arrivals` 엔드포인트를 추가해 Idempotency-Key 기반 수동 도착 확정을 지원했다.(`src/main/java/com/matjom/matjom/visit/api/VisitSessionController.java:47`)
- `VisitSessionService`에 멱등 처리, 시간·거리 검증, 상태 전이를 수행하는 `confirmManualArrival` 로직을 구현했다.(`src/main/java/com/matjom/matjom/visit/service/VisitSessionService.java:66`)
- 거리 계산을 공통 유틸 `GeoDistanceCalculator`로 분리해 지오펜스와 수동 도착이 동일 하버사인 로직을 사용하도록 정리했다.(`src/main/java/com/matjom/matjom/visit/util/GeoDistanceCalculator.java:1`)
- 요청/응답 DTO(`VisitManualArrivalRequest/Response`)를 정의하고 유효성 검증을 적용했다.(`src/main/java/com/matjom/matjom/visit/dto/VisitManualArrivalRequest.java:1`, `src/main/java/com/matjom/matjom/visit/dto/VisitManualArrivalResponse.java:1`)
- 새로운 예외 코드 `ARRIVAL_TIME_INVALID`, `ARRIVAL_DISTANCE_EXCEEDED`를 추가했다.(`src/main/java/com/matjom/matjom/common/exception/message/ErrorCode.java:33`)
- OpenAPI 스펙에 수동 도착 요청/응답과 예외 시나리오를 문서화했다.(`docs/openapi/openapi-v1.yaml:312`)

## 동작 흐름
1. 컨트롤러가 멱등 키를 검증하고 서비스에 위임한다.
2. 서비스는 멱등 저장소에서 동일 요청 여부를 확인한다.
3. 세션 상태가 ACTIVE인지 확인 후 시작 시각과 현재 시각 차이를 계산한다.
4. `GeoDistanceCalculator`로 장소와 요청 좌표 간 거리를 측정한다.
5. 시간·거리 조건이 충족되면 세션을 ARRIVED로 전환하고 응답을 저장한다.
6. 동일 멱등 키로 재호출 시 `replayed=true` 응답을 돌려준다.

## 테스트
- `VisitSessionServiceTest`에 정상 전환, 거리 초과, 시간 위반, 멱등 재생 케이스를 추가했다.(`src/test/java/com/matjom/matjom/visit/service/VisitSessionServiceTest.java:88`)
- 기존 지오펜스 테스트는 유틸 추출로 변경된 메서드 호출만 영향을 받았다.

## 후속 TODO
- 수동 도착 이벤트 로깅과 감사 데이터 적재를 위한 메타 저장 전략 정의.
- 운영 권한 체계와 연동하여 `requestedBy` 검증을 강화할 필요가 있다.
