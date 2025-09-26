# 서브 이슈 #24-6: feat(session) 타임아웃 및 상태 전이 스케줄러

## 배경/문제 정의
- 세션이 무한정 ACTIVE 상태로 남아 있어 데이터가 오염되고 있다.
- 타임아웃 기준(30분)이 있음에도 자동 만료 로직이 구현되지 않았다.

## 사용자 스토리
As 서비스 운영자,
I want 세션이 시작 후 30분이 지나면 자동으로 만료되길 원한다
so that 비정상 세션을 수동으로 정리할 필요가 없다.

## 범위 (In/Out)
**In**
- 타임아웃 스케줄러(Quartz/Spring Scheduling/Redis Delayed Job 중 선택)
- 만료 시 상태를 EXPIRED로 전환하고 이벤트 기록
- 재기동 시 중복 실행 방지 로직

**Out**
- GeoFence 계산 및 도착 판정
- 운영자에 의한 수동 취소 UI

## 수용 기준 (AC)
- [ ] Given 세션이 STARTED/ACTIVE 상태일 때, When 30분이 경과하면, Then 세션 상태가 EXPIRED로 변경되고 이벤트 로그가 남는다.
- [ ] Given 서비스가 재기동되더라도, Then 타임아웃 스케줄이 중복 실행되지 않는다.
- [ ] Given 세션이 이미 ARRIVED/CANCELLED 되었다면, Then 타임아웃 스케줄은 자동으로 해제된다.

## API/계약 영향
- 타임아웃 관련 이벤트 로그 스키마 추가
- 필요 시 `/api/v1/sessions/{id}` 조회 응답에 `expiresAt` 필드 노출

## 비기능 요구사항 (NFR)
- 타임아웃 큐는 최소 30분 이상 정확도를 유지해야 한다.
- 장애 후 복구 시간(Recovery Time Objective) ≤ 5분 내 스케줄 재등록

## 의존성/리스크
- Redis 혹은 스케줄러 의존성 선택 시 운영팀과 협의 필요
- 대량 세션(>10만) 처리 시 스케줄러 성능 검토 필요
