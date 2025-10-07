# 4.7 상태 전이 이벤트 로깅 & 권한 트리거 선행 학습 가이드 (주니어 개발자용 0→100)

## 1. 목표 요약
- **무엇**: 세션 상태가 바뀔 때마다 이벤트를 표준화된 구조로 저장하고, ARRIVED 시 사용자 권한(검색 레이트리밋)을 초기화한다.
- **왜**: 운영/분석에서 상태 전이 근거가 필요하며, 도착 이용자에게 즉시 재추천 기회를 제공하기 위함.
- **완료 조건**
  1. ARRIVED/EXPIRED/CANCELLED 이벤트가 `VisitEvent` 또는 `Visit.meta.history`에 기록된다.
  2. ARRIVED 이벤트 시 사용자 검색 레이트리밋 키가 초기화된다.
  3. 이벤트 로깅이 실패하면 세션 상태 변경이 롤백되거나 오류로 표면화된다.

## 2. 현재 코드 구조 이해
1. **세션 상태 변경 포인트**
   - `VisitSessionService` : 세션 시작, 수동 도착 처리.
   - `VisitPositionService` : 자동 도착(GeoFence) 시 상태 전환.
   - `VisitTimeoutScheduler` : 30분 경과 시 EXPIRED로 전환.
2. **Visit 엔터티** (`src/main/java/com/matjom/matjom/visit/entity/Visit.java`)
   - 상태 필드(`VisitState`)와 meta JSON 슬롯이 존재.
3. **레이트리밋**
   - `RateLimitFilter`가 Redis 키 `rl:places:user:{userId}`/`rl:places:ip:{ip}` 를 사용.
   - ARRIVED 시 사용자 키 삭제로 초기화 가능.

## 3. 이벤트 저장 설계
### 3.1 VisitEvent 엔터티 도입 (권장)
- 필드: `id`, `visit`, `userId`, `eventType`, `fromState`, `toState`, `occurredAt`, `meta(JSONB)`.
- 인덱스: `visit_id, occurred_at`.
- 이점: SQL 조회, 확장성, 감사 용도.

### 3.2 Visit.meta.history 활용 (대안)
- 소규모 환경에서 별도 테이블 없이 JSON 히스토리로 관리.
- 그러나 검색/집계가 어려우므로 중장기적으로 VisitEvent 테이블을 권장.

## 4. 권한 트리거 설계
- 대상: ARRIVED 이벤트(자동/수동 모두).
- 행동: Redis 레이트리밋 키 삭제 (`StringRedisTemplate.delete("rl:places:user:" + userId)`).
- 오류 처리: Redis 삭제 실패 시 로그 남기고 예외 전파? (AC 조건상 실패 시 롤백 고려 → 트랜잭션 안에서 예외 던짐).

## 5. 구현 단계 체크리스트
1. `VisitEventType` enum 정의 (`ARRIVED`, `ARRIVED_MANUAL`, `EXPIRED`, `CANCELLED`).
2. `VisitEvent` 엔터티 + `VisitEventRepository` 생성.
3. `VisitEventService` 작성: `recordTransition(Visit visit, VisitState from, VisitState to, VisitEventType type, OffsetDateTime occurredAt, JsonNode meta)`.
4. 레이트리밋 초기화용 `VisitPrivilegeService`(가칭) 작성 → Redis 키 삭제.
5. 상태 변경 지점에서 before/after 상태 비교 후 이벤트 서비스 호출.
   - `VisitSessionService.processManualArrival`
   - `VisitPositionService.recordPosition`
   - `VisitTimeoutScheduler.expireTimedOutVisits`
6. 예외 처리: 이벤트 저장 실패 시 `SessionException(ErrorCode.INTERNAL_SERVER_ERROR)` 등으로 전파해 상태 변경도 롤백.
7. 테스트 작성: 이벤트가 저장되는지, 레이트리밋 키 삭제가 호출되는지.

## 6. 테스트 전략 세부
- **단위**
  - `VisitEventServiceTest`: 상태/메타가 올바르게 저장되는지.
  - `VisitPrivilegeServiceTest`: Redis 키 삭제 시도 검증.
- **통합**
  - `VisitSessionServiceTest`: 수동 도착 → 이벤트 저장/레이트리밋 초기화 확인.
  - `VisitTimeoutSchedulerTest`: 만료 → 이벤트 저장 확인.
- **회귀**
  - `./gradlew test`

## 7. 예상 리스크 및 대응
1. **이벤트 중복**: 멱등키를 eventId로 관리하거나 `(visitId,eventType,occurredAt)` 복합 unique 제약 고려.
2. **Redis 삭제 실패**: 네트워크 장애 시 재시도 로직 또는 경고 로그 필요.
3. **성능**: 대량 이벤트 저장 시 배치 insert 또는 비동기 처리 고려.

## 8. 참고 자료
- Spring Data JPA Auditing (`BaseEntity`)로 createdAt 자동 기록.
- RedisTemplate docs: https://docs.spring.io/spring-data/data-redis/docs/current/reference/html/
- 이벤트 소싱 패턴 개요: https://martinfowler.com/eaaDev/EventSourcing.html

> 위 가이드는 상태 전이 이벤트 로깅과 권한 트리거를 구현하기 전에 알아야 할 개념과 설계 결정을 정리해, 주니어 개발자도 스스로 구현을 시작할 수 있도록 돕는다.
