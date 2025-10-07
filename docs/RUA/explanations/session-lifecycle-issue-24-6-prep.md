# 4.6 타임아웃 스케줄러 선행 학습 가이드 (주니어 개발자용 0→100)

## 1. 작업 목표 요약
- **무엇을 만들까?** 세션이 시작된 뒤 30분이 지나면 자동으로 `EXPIRED` 상태로 전환하는 타임아웃 스케줄러.
- **왜 필요한가?** ACTIVE 상태로 방치된 세션을 자동 정리해 데이터 정합성과 운영 편의성을 확보하기 위함.
- **완료 조건**
  1. 세션 시작 시 타임아웃 예약이 등록된다.
  2. 30분이 경과하면 세션이 `EXPIRED` 로 변경되고 필요한 로그/이벤트를 남긴다.
  3. 애플리케이션 재기동 시 중복 없이 예약을 복구하거나 재계산한다.

## 2. 현재 코드 구조 이해
1. `VisitSessionService.startSession(...)`(`src/main/java/com/matjom/matjom/visit/service/VisitSessionService.java`)
   - 세션 생성과 멱등 처리를 담당. 여기서 타임아웃 예약을 추가해야 한다.
2. `Visit` 엔터티(`src/main/java/com/matjom/matjom/visit/entity/Visit.java`)
   - `startedAt`, `state`, `expiredAt` 필드 보유. 현재는 만료 로직이 수동으로만 가능.
3. 저장소/서비스
   - `VisitRepository` : 세션 조회 및 상태 업데이트.
   - 향후 타임아웃을 관리할 별도 서비스(예: `VisitTimeoutService`)를 두면 역할 분리 가능.
4. 공통 인프라
   - Redis 의존성이 이미 존재(Idempotency, RateLimit). Delayed Queue를 사용할지, Spring `@Scheduled`+DB 스캔을 사용할지 결정필요.

## 3. 타임아웃 구현 전략 옵션 비교
| 옵션 | 장점 | 단점 | 적용 방법 |
| --- | --- | --- | --- |
| Spring `@Scheduled` + DB 스캔 | 구현 간단, 추가 인프라 불필요 | 매 분 스캔 시 DB 부담, 대량 세션 시 성능 이슈 | 매 분 `startedAt + 30m <= now` 조건으로 UPDATE |
| Redis Sorted Set (ZSET) | 정밀 타이머, 재기동 복구 용이 | Redis 의존성 강화, Lua 스크립트 필요 | `ZADD timeoutKey score=sessionTimeout` 후 `ZRANGEBYSCORE` 폴링 |
| Spring TaskScheduler + Persistent Store | 고정밀 타이머 | 재기동 시 휘발 → 복구 로직 직접 구현 필요 | 예약 정보 DB/Redis 저장 후 재등록 |

> **추천**: MVP 단계에선 `@Scheduled` + DB 배치 스캔이 구현 난이도가 낮고 복구 로직이 단순하다. 성능 이슈 발생 시 Redis 기반으로 확장.

## 4. 상세 설계(권장안: 스케줄러 + DB 스캔)
1. **타임아웃 예약 시점**
   - 세션 생성 후 `Visit` 엔터티에 예상 만료 시각(`expiresAt = startedAt.plusMinutes(30)`)을 저장.
   - 이미 `VisitSessionStartResponse`가 `expiresAt`을 내려주고 있으므로 엔터티에도 필드 반영 필요.
2. **스케줄러 구성**
   - `@Component` class + `@Scheduled(fixedDelay = 60000)` (1분 주기) 또는 cron.
   - 실행 시 `visitRepository.findTimeoutCandidates(now)` 같은 쿼리 호출.
3. **쿼리 설계**
   - 조건: `state = ACTIVE`, `startedAt <= now - 30분` (또는 `expiresAt <= now`).
   - 다중 행 업데이트: `UPDATE visits SET state='EXPIRED', expired_at=now WHERE ... RETURNING visit_id`. JPA/Querydsl/Native 중 선택.
4. **동시성 제어**
   - 스케줄러가 한 번만 실행되도록 `@Scheduled` 메서드 내부에 분산락 또는 `synchronized`.
   - 초기엔 단일 인스턴스 기준 `synchronized`나 `ReentrantLock`으로 충분. 향후 다중 서버면 Redis 기반 락(Lettuce `SETNX` + TTL) 도입.
5. **로그/이벤트**
   - 만료된 세션 수를 로그로 남기고, 향후 `VisitEvent` 테이블에 기록하거나 `meta.history`에 추가할 수 있다.

## 5. 구현 단계 체크리스트
1. Visit 엔터티에 `expiresAt` 필드 추가(없다면) 및 게터/세터 작성.
2. 세션 시작 시 `visit.setExpiredAt(startedAt.plusMinutes(30))` 호출 후 저장.
3. `VisitTimeoutScheduler` (가칭) 클래스 생성 → `@Scheduled` 메서드 구현.
4. 후보 조회 메서드 작성 (JPA 쿼리, Querydsl, Native 선택) → batch update.
5. 상태 전환 시 `Visit.transitionTo(VisitState.EXPIRED)` 호출 및 `expiredAt` 갱신.
6. 테스트 작성: 서비스 단위 테스트 + 스케줄러 통합 테스트 (시간 제어를 위해 `Clock` 또는 `OffsetDateTime` mocking).
7. 문서/스펙 업데이트: OpenAPI에 `expiresAt` 명세, 작업 문서 체크박스 갱신.

## 6. 테스트 전략 세부
- **단위**: `VisitTimeoutServiceTest`에서 가짜 세션 데이터를 만들어 만료 전/만료 후 상태 확인.
- **통합**: H2/테스트 DB에 샘플 데이터 삽입 후 스케줄러 실행, 업데이트 수 검증.
- **회귀**: `./gradlew test` 전체 실행.

## 7. 잠재 리스크 및 대응
1. **대량 세션 성능**: 인덱스(`state`, `expires_at`) 필요 여부 검토.
2. **재기동 복구**: 애플리케이션 다운 동안 누락된 타임아웃을 재검토하려면, 기동 직후 스케줄러가 즉시 한 번 실행되도록 설정.
3. **도착/취소와 충돌**: 세션이 이미 ARRIVED/CANCELLED 되면 스케줄러에서 필터링 필요.

## 8. 참고 자료
- Spring Scheduling 공식 문서: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Redis 기반 지연 큐 구현 예시: https://redis.io/docs/data-types/sorted-sets/
- MatJom 기존 RateLimit/Idempotency 모듈: Redis 사용 패턴 참고.

> 이 가이드를 따라 준비하면 4.6 타임아웃 스케줄러 구현 전 필요한 개념과 코드 위치를 명확히 이해하고 착수할 수 있다.
