# 4.8 세션 라이프사이클 경계 테스트 선행 학습 가이드 (주니어 개발자용 0→100)

## 1. 작업 목표 요약
- **무엇을 검증하나?** 세션 라이프사이클의 핵심 조건(반경 30m, dwell 3분, 유예 10초, 타임아웃 30분 등)이 경계 값에서도 정확히 동작하는지 자동화 테스트를 완성한다.
- **왜 필요한가?** 경계값(29.9m, 179s 등)에서 판정이 틀어지면 운영 이슈로 직결되므로, 테스트로 회귀 방지 장치를 마련해야 한다.
- **완료 기준**
  1. 자동 도착 판정에 대한 경계 케이스 테스트 (29.9m/179s 미도착, 30.0m/180s 도착 등).
  2. 유예 시간(10초) 재진입 시 dwell 유지/초과 시 초기화가 검증된다.
  3. 타임아웃/권한 트리거/이벤트 로깅이 경계 시나리오에서도 일관되게 동작한다.

## 2. 현재 테스트 지형
- `DefaultGeoFenceEvaluatorTest`: 도착 판정·정확도 가드·유예 로직 테스트.
- `VisitPositionServiceTest`: 위치 처리 후 상태 전이를 검증.
- `VisitTimeoutSchedulerTest`: 타임아웃 만료 로직 테스트.
- `VisitSessionServiceTest`: 수동 도착 멱등·거리·시간 조건 테스트.
- `VisitStateTransitionRecorderTest`, `VisitPrivilegeServiceTest`: 이벤트 기록/레이트리밋 초기화 검증.
→ 4.8에서는 **경계값 중심**으로 기존 테스트를 보강하거나 통합 테스트를 추가해야 한다.

## 3. 필수 경계 시나리오
1. **자동 도착 (GeoFence)**
   - 29.9m & dwell 179s → 도착 실패.
   - 30.0m & dwell 180s → 도착 성공.
   - 정확도(accuracy) 50m 이벤트로 dwell pause → 이후 정확도 5m로 회복 시 dwell 재개.
2. **유예 10초**
   - 30m 내 dwell 시작 후 9초간 이탈 → dwell 유지.
   - 30m 내 dwell 시작 후 11초간 이탈 → dwell 초기화.
3. **타임아웃 30분**
   - `startedAt + 29분 59초` → ACTIVE 유지.
   - `startedAt + 30분 01초` → EXPIRED, 이벤트 생성, 권한 리셋 없음.
4. **수동 도착**
   - 9분 59초 요청 → `ARRIVAL_TIME_INVALID`.
   - 10분 00초~59분 59초 → 허용 + 이벤트 + 권한 초기화.
   - 60분 01초 → `ARRIVAL_TIME_INVALID`.
5. **이벤트/권한**
   - 상기 모든 경계 상황에서 `VisitEvent` 기록 및 레이트리밋 초기화가 올바르게 호출되는지 검증.

## 4. 구현 전략
- **단위 테스트 확장**
  - `DefaultGeoFenceEvaluatorTest`: 거리/시간 경계 케이스 추가.
  - `VisitPositionServiceTest`: dwell 유지/초기화, ARRIVED 이벤트 recorder 호출 검증.
  - `VisitTimeoutSchedulerTest`: 29분/30분 비교 + recorder 호출 검증.
  - `VisitSessionServiceTest`: 수동 도착 시간 경계 테스트 강화.
- **통합 테스트(선택)**
  - SpringBootTest + Testcontainers(Postgres/Redis)를 사용해 실제 DB/Redis와 함께 e2e 흐름 검증.
  - 또는 Mockito 기반으로 Clock/GeoFenceEvaluator를 제어해 시나리오 테스트 작성.

## 5. Mocking & 시간 제어
- `Clock` 빈(이미 `ClockConfig`)을 주입 받아 고정 시각으로 테스트.
- GeoFenceEvaluator는 실제 계산을 사용하거나 거리를 직접 설정 (Haversine util 활용).
- Redis 의존성은 Mockito로 verify.

## 6. 테스트 작성 순서 제안
1. GeoFence evaluator 경계 테스트 추가.
2. VisitPositionService dwell/유예 경계 테스트 추가.
3. VisitTimeoutScheduler 경계 테스트 추가.
4. VisitSessionService 수동 도착 시간 경계 테스트 강화.
5. 이벤트 기록/권한 트리거에 대한 verify 추가.
6. 필요 시 통합 테스트 구성.

## 7. 리스크 및 대응
- **테스트 불안정성**: `OffsetDateTime.now()` 대신 고정 Clock 사용.
- **성능**: 통합 테스트가 많으면 Testcontainers 비용 증가 → 최소화.
- **Mock 설정 복잡성**: `ArgumentCaptor`로 이벤트 payload 검증.

## 8. 참고 자료
- `docs/RUA/session-concept-guide.md` : 정책 요약.
- `docs/RUA/explanations/session-lifecycle-issue-24-7-impl.md` : 이벤트/권한 로직 참조.
- JUnit & Mockito 공식 문서 (verify, captor, fixed clock 활용).

> 이 가이드를 따르면 경계값 테스트 설계에 필요한 배경과 구현 순서를 명확하게 파악할 수 있어, 주니어 개발자도 안정적인 4.8 테스트 작업을 진행할 수 있다.
