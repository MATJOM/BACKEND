# 서브 이슈 #24-3: feat(session) GeoFenceEvaluator (반경 30m, dwell 3분, 유예 10초)

## 배경/문제 정의
- 방문 판정의 핵심 규칙이 문서로만 존재하고 코드 구현이 없어 일관성 있는 도착 판정을 할 수 없다.
- 반경 계산, dwell 누적, 유예 시간 등의 로직을 표준화하지 않으면 테스트와 운영이 불가능하다.

## 사용자 스토리
As 서비스 운영자,
I want 시스템이 사용자의 위치 이벤트를 기준 반경과 dwell 정책에 따라 자동 판정해 주길 원한다
so that 도착 여부가 명확하고 수동 개입이 줄어든다.

## 범위 (In/Out)
**In**
- GeoFenceEvaluator 도메인 서비스 구현
- 입력: 세션 정보, 현재 위치, 이전 dwell 상태, Redis 캐시
- 출력: dwell 누적 값, 상태 전환 여부, 유예 타이머 상태

**Out**
- 정확도 가드(4.4)
- 수동 도착(4.5)
- UI/알림 로직

## 수용 기준 (AC)
- [ ] Given 사용자가 반경 30m 이내에 3분 이상 머무르면, Then evaluator는 `ARRIVED` 판정 플래그를 반환한다.
- [ ] Given 사용자가 반경 밖으로 나갔다가 10초 이내에 복귀하면, Then 누적 dwell이 유지된다.
- [ ] Given 사용자가 10초 이상 벗어나면, Then dwell이 초기화된다.
- [ ] Given Redis에 캐시된 dwell 정보가 없으면, Then 기본값(0초)으로 계산을 시작한다.

## API/계약 영향
- evaluator 결과를 `PositionResponse`에 반영: `shouldArrive`, `dwellSeconds`, `graceRemaining`
- Redis 키 스키마: `session:dwell:{sessionId}` → `{totalSeconds, lastEventTs, graceExpiryTs}`

## 비기능 요구사항 (NFR)
- 계산 로직은 순수 함수 형태로 구현해 단위 테스트가 용이해야 한다.
- 거리 계산 오차 ±2m 이내 (PostGIS `ST_DWithin` 혹은 Haversine 일관성 확보)
- Redis 호출 실패 시에도 안전하게 동작하도록 폴백(메모리) 전략 마련

## 의존성/리스크
- 위치 API(4.2)와 정확도 가드(4.4)와 긴밀히 연결 → 인터페이스 합의 필요
- Redis TTL, grace timer 관리 정책 미정 → 운영팀과 협의 필요
- 배치/스케줄러(4.6)와 상태 전이 충돌 우려 → 상태 머신 설계시 주의
