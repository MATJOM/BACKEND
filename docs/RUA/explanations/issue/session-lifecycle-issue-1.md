# 서브 이슈 #24-1: feat(session) 세션 시작 API 및 멱등키 검증

## 배경/문제 정의
- 사용자 한 명이 하루에 여러 번 추천을 실행할 때 중복 세션이 생겨 데이터가 혼재하고 있다.
- 현재 `/api/v1/sessions` 엔드포인트가 없어 세션 시작 이벤트를 기록할 수 없다.
- 멱등키 미지원으로 재시도 시 중복 레코드가 지속적으로 쌓인다.

## 사용자 스토리
As 점심 추천을 체험하는 사용자,
I want 세션을 시작할 때 중복 없이 단 하나의 세션이 생성되길 원한다
so that 추천 결과와 방문 여부가 정확히 매칭된다.

## 범위 (In/Out)
**In**
- `POST /api/v1/sessions` 컨트롤러, 서비스, 리포지토리 구현
- 사용자/장소/멱등키/선호 반경 등 입력 검증
- ACTIVE 세션 존재 여부 확인 및 예외 처리

**Out**
- 위치 이벤트 수집(4.2 이후 처리)
- 도착/타임아웃 로직
- 프런트엔드 변경 및 알림 시스템

## 수용 기준 (AC)
- [ ] Given 동일 사용자가 멱등키를 두 번 보낼 때, When 두 번째 호출이 도착하면, Then 첫 번째와 동일한 세션 ID를 반환한다.
- [ ] Given 동일 사용자가 ACTIVE 세션을 보유 중일 때, When 새로운 세션 시작을 요청하면, Then `SESSION_ALREADY_ACTIVE` 에러가 반환된다.
- [ ] Given 필수 파라미터(lat/lng/userId/placeId)가 누락되면, Then 400 응답과 상세 메시지가 반환된다.

## API/계약 영향
- `POST /api/v1/sessions` 요청/응답 스키마 추가 (OpenAPI 업데이트)
- 응답 구조: `sessionId`, `state`, `startedAt`, `expiresInSeconds`
- 에러 코드: `SESSION_ALREADY_ACTIVE`, `IDEMPOTENCY_KEY_REQUIRED`, `INVALID_REQUEST_PARAM`

## 비기능 요구사항 (NFR)
- 멱등 응답 재생 TTL 60초 유지
- DB Unique 제약: `(user_id, state='ACTIVE')`
- 요청 처리 p95 ≤ 200ms (캐싱/트랜잭션 포함)

## 의존성/리스크
- 사용자/장소 ID 검증을 위해 기존 레포지토리 혹은 외부 서비스 연동 필요 여부 확인
- Redis 가용성 문제 시 멱등 처리가 불안정해질 수 있음 → 폴백 전략 검토
- 향후 세션 확장(멀티 디바이스) 시 멱등키 정책 재검토 필요
