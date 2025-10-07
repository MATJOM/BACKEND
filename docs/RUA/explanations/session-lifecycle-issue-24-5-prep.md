# 4.5 수동 도착 확정 선행 학습 가이드

## 1. 개요
- 목적: 자동 지오펜스가 실패했을 때 운영자가 수동으로 세션을 ARRIVED 상태로 전환할 수 있는 API를 구현한다.
- 범위: `POST /api/v1/sessions/{sessionId}/arrivals` 엔드포인트, 요청/응답 DTO, 서비스 계층 로직, 멱등 처리, 검증 규칙.
- 선행 작업 종속성: 4.1 세션 시작, 4.2 위치 수집, 4.3/4.4 지오펜스 로직이 완료되어 있어야 함.

## 2. 도메인 요구사항 정리
1. **대상 세션**
   - 상태가 `ACTIVE` 인 세션만 수동 도착 처리 가능.
   - 이미 `ARRIVED`, `EXPIRED`, `CANCELLED` 상태면 409 충돌 응답.
2. **시간 조건**
   - 세션 `startedAt` 기준 10분 미만이면 사용자가 아직 이동 중일 가능성이 높으므로 수동 확정 금지.
   - 60분 초과 시 세션이 자연 만료 대상이므로 수동 확정 금지.
   - 시간 계산은 서버 기준 `Asia/Seoul` 타임존을 따르며, 허용 범위는 [10분, 60분] 구간이다.
3. **위치 조건**
   - 요청 본문에 전달된 `latitude, longitude` 와 장소의 좌표를 하버사인 계산으로 비교.
   - 거리 ≤ 30m 일 때만 수동 확정 가능. (자동 도착과 동일 기준)
4. **감사 요건**
   - `requestedBy`(운영자 식별자) 필수.
   - 추후 이벤트 로깅/메트릭 연동을 위해 `meta` 혹은 로그에 남긴다.
5. **멱등 처리**
   - `Idempotency-Key` 헤더 필수. 동일 키로 60초 이내 재요청 시 동일 응답 재생.
   - 입력 본문이 다르면 `IDEMPOTENCY_KEY_CONFLICT` 로 거절.

## 3. 설계 지침
### 3.1 컨트롤러 계층
- `VisitSessionController` 에 `@PostMapping("/{sessionId}/arrivals")` 추가.
- 헤더 `Idempotency-Key` 유효성 검증 재사용(4.1과 동일한 `validateIdempotencyKey`).
- 요청 DTO `VisitManualArrivalRequest` (위도/경도/요청자/요청시각) 정의.
- 응답 DTO `VisitManualArrivalResponse` 에 `state`, `arrivedAt`, `replayed` 필드를 포함.

### 3.2 서비스 계층
- `VisitSessionService` 혹은 별도 `VisitArrivalService` 에서 비즈니스 로직 구현.
- 처리 순서 예시:
  1. 멱등 키 조합 → Redis 저장소 조회.
  2. 세션 조회 및 상태 검증.
  3. 시간 범위 검증(10~60분) → 실패 시 `ARRIVAL_TIME_INVALID` 예외.
  4. 거리 계산 → 실패 시 `ARRIVAL_DISTANCE_EXCEEDED` 예외.
  5. 방문을 `arriveAt(now)` 로 전환, `VisitRepository.save`.
  6. `VisitManualArrivalResponse` 생성 후 멱등 저장.
- 거리 계산은 `DefaultGeoFenceEvaluator` 의 `distanceMeters` 를 재사용할 수 있도록 유틸 분리 또는 별도 메서드 구현.

### 3.3 예외 및 에러 코드
- 신규 에러 코드 권장:
  - `ARRIVAL_TIME_INVALID` (400) — 시간 조건 위반.
  - `ARRIVAL_DISTANCE_EXCEEDED` (400) — 거리 조건 위반.
  - 필요 시 `SESSION_ALREADY_INACTIVE` (409) 또는 별도 코드 `SESSION_INACTIVE` 사용.
- `SessionException` 을 활용하여 HTTP 상태 및 메시지 제어.

### 3.4 멱등 저장 키 전략
- Prefix 제안: `idemp:sessions:arrival:{sessionId}:` + 헤더 키.
- 요청 본문 해시(SHA-256) → `IdempotencyStore.replayOrRun` 사용.
- 응답 DTO 직렬화를 위한 `ObjectMapper` 재사용.

## 4. 테스트 전략
1. **정상 흐름**: ACTIVE 세션, 시간/거리 조건 충족 → ARRIVED 반환, `arrivedAt` 설정.
2. **시간 위반**: start 이후 5분 → `ARRIVAL_TIME_INVALID`.
3. **시간 초과**: start 이후 70분 → `ARRIVAL_TIME_INVALID`.
4. **거리 초과**: 45m → `ARRIVAL_DISTANCE_EXCEEDED`.
5. **세션 상태**: 이미 ARRIVED → `SESSION_ALREADY_INACTIVE`.
6. **멱등 재생**: 동일 키 재호출 시 `replayed=true`.

## 5. 구현 체크리스트
- [ ] 요청/응답 DTO 생성 및 검증 애너테이션 설정.
- [ ] 컨트롤러에 엔드포인트 및 멱등 헤더 검사 추가.
- [ ] 서비스 로직 구현(시간·거리 검증, 상태 전환, 멱등 저장).
- [ ] 거리 계산 재사용 유틸 마련.
- [ ] 예외 코드/메시지 추가 및 국제화 메시지 연결(필요 시).
- [ ] 단위 테스트 작성(서비스, 컨트롤러 슬라이스 등).
- [ ] OpenAPI 스펙 및 작업 문서 업데이트.

## 6. 주니어 개발자용 단계별 가이드
1. 문서를 읽고 기존 4.1~4.4 구현 스타일을 파악한다.
2. 필요한 DTO/에러 코드를 정의한다.
3. 서비스 레이어에 멱등 처리 패턴을 복습한다(`VisitSessionService.startSession`).
4. 거리 계산 로직 구현 후 단위 테스트로 정확도를 검증한다.
5. 컨트롤러에 엔드포인트를 추가하고 통합 테스트를 작성한다.
6. OpenAPI 스펙을 수정하고 `docs/RUA/tasks-prd-v1-점심-추천-mvp.md` 체크리스트를 갱신한다.
7. `./gradlew test` 로 전체 회귀 테스트 수행 후 결과를 기록한다.
