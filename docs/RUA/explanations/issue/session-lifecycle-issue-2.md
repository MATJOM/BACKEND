# 서브 이슈 #24-2: feat(session) 위치 수신 API와 GeoFenceEvaluator 연동

## 배경/문제 정의
- 세션이 시작되어도 사용자의 이동 데이터가 적재되지 않아 dwell 계산을 수행할 수 없다.
- 위치 이벤트를 기록하는 표준 API가 없어 클라이언트 팀과 연동이 지연되고 있다.

## 사용자 스토리
As 세션을 진행 중인 사용자,
I want 앱이 주기적으로 내 현재 위치를 백엔드에 전송하길 원한다
so that 서비스가 기준 반경 내 체류 시간을 정확히 추적할 수 있다.

## 범위 (In/Out)
**In**
- `POST /api/v1/sessions/{sessionId}/positions` 컨트롤러/서비스 구현
- 위치 이벤트 DB 저장 및 GeoFenceEvaluator 호출
- 응답에 누적 dwell, 현재 상태, 정확도 플래그 전달

**Out**
- GeoFenceEvaluator 세부 로직(4.3에서 담당)
- 정확도 가드(4.4에서 담당)
- 레이트리밋/권한 체크(선행 모듈 활용 여부만 검토)

## 수용 기준 (AC)
- [ ] Given 유효한 위치 데이터가 들어올 때, When 저장이 성공하면, Then 위치 레코드가 DB에 추가되고 응답에 최신 dwell 값이 포함된다.
- [ ] Given 존재하지 않는 세션 ID가 들어오면, Then 404 `SESSION_NOT_FOUND` 응답을 반환한다.
- [ ] Given 세션이 ARRIVED/EXPIRED 상태일 때, Then 409 `SESSION_INACTIVE` 에러를 반환한다.

## API/계약 영향
- 위치 요청 DTO: `latitude`, `longitude`, `accuracy`, `recordedAt`
- 응답 DTO: `state`, `dwellSeconds`, `accuracyPaused`
- OpenAPI에 200/404/409/400 시나리오 추가

## 비기능 요구사항 (NFR)
- 요청당 GeoFenceEvaluator 호출 p95 ≤ 300ms
- 위치 이벤트는 최대 초당 2회 처리(레이트리밋 고려)
- 데이터는 최대 30일 보관(백오피스 요구), TTL 설정 TBD

## 의존성/리스크
- GeoFenceEvaluator가 아직 구현되지 않아 Mock 또는 임시 로직 필요 → 4.3 완료 시점과 연계
- 위치 저장 트랜잭션 중 Redis 호출 실패 시 롤백 전략 명확화 필요
- Testcontainers(PostGIS, Redis) 환경에서 통합 테스트 시 성능 저하 가능
