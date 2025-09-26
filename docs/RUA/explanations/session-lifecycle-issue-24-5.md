# 서브 이슈 #24-5: feat(session) 수동 도착 확인 API

## 배경/문제 정의
- GPS 환경에 따라 자동 도착이 실패할 수 있어 운영자가 수동으로 도착을 확정할 수단이 필요하다.
- 현재는 DB를 직접 수정해야 해 실수와 장애 위험이 크다.

## 사용자 스토리
As 고객 지원 담당자,
I want 사용자가 요청했을 때 수동으로 도착을 확정할 수 있길 원한다
so that 잘못된 도착 판정을 신속하게 보정할 수 있다.

## 범위 (In/Out)
**In**
- `POST /api/v1/sessions/{id}/arrivals` API 구현
- 입력 검증: 반경 ≤30m, 세션 시작 후 10~60분 사이
- ARRIVED 상태 업데이트 + 이벤트 로깅

**Out**
- 자동 도착 로직(4.3)
- 타임아웃/취소 처리
- 운영툴 UI

## 수용 기준 (AC)
- [ ] Given 세션이 ACTIVE 상태이고 반경 ≤30m일 때, When 수동 도착을 호출하면, Then 세션이 ARRIVED 상태로 변경되고 도착 시각이 기록된다.
- [ ] Given 세션이 이미 ARRIVED/EXPIRED/CANCELLED이면, Then 409 `SESSION_INACTIVE` 에러를 반환한다.
- [ ] Given 요청이 세션 시작 10분 미만 혹은 60분 초과라면, Then 400 `ARRIVAL_TIME_INVALID` 에러를 반환한다.

## API/계약 영향
- 요청 DTO: `latitude`, `longitude`, `requestedBy`
- 응답 DTO: `state=ARRIVED`, `arrivedAt`
- 이벤트 로그: `event=ARRIVED_MANUAL`

## 비기능 요구사항 (NFR)
- 멱등 처리 필수(Idempotency-Key) — 반복 요청 시 동일 응답 재생
- 감사 로그(누가 언제 수동 도착을 처리했는지) 남기기

## 의존성/리스크
- 정확도 가드와의 상호작용: 수동 도착 시 accuracy 검증을 어떻게 처리할지 합의 필요
- 운영자 권한 체계와 연동 필요 (현재는 placeholder)
