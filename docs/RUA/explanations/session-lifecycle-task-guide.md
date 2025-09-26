# 세션 라이프사이클 4.x 태스크 사전 학습 가이드

주니어 개발자도 4.x 태스크를 0에서 100까지 이해하고 구현할 수 있도록 필수 지식과 준비 절차를 정리했다.

## 1. 핵심 개념 정리

| 용어 | 설명 |
| --- | --- |
| 세션(Session) | 추천 서비스에서 사용자가 점심 장소를 탐색하거나 방문을 시도할 때 생성되는 상태 단위. |
| 상태 전이 | STARTED → ACTIVE → ARRIVED/EXPIRED/CANCELLED 의 단일 방향 라이프사이클. |
| dwell | 사용자가 특정 반경(30m) 안에서 요구 시간(3분) 이상 머무르는지 판단하기 위한 누적 체류 시간. |
| 유예 10초 | dwell 누적 중 위치 이탈이 발생해도 10초 이내 복귀 시 누적 시간을 유지하는 정책. |
| 정확도 가드 | GPS accuracy가 30m 초과인 위치 이벤트는 dwell 계산에서 일시 제외. |
| 멱등키 | `/sessions/start`, `/sessions/arrivals` 등에서 동일 요청 반복 시 중복 처리를 방지하기 위한 헤더. |

## 2. 데이터 모델 및 테이블 구조

### 2.1 세션 엔터티 (예상)
- `visit_sessions`
  - `id` (PK)
  - `user_id`
  - `place_id`
  - `state` (ENUM: STARTED, ACTIVE, ARRIVED, EXPIRED, CANCELLED)
  - `started_at`, `arrived_at`, `expired_at`, `cancelled_at`
  - `created_at`, `updated_at`
  - `idempotency_key`

### 2.2 위치 이벤트 엔터티
- `visit_positions`
  - `id` (PK)
  - `session_id`
  - `latitude`, `longitude`, `accuracy`
  - `recorded_at`
  - `created_at`

### 2.3 Redis 키 활용
- `session:dwell:{sessionId}` → 누적 체류 시간(초), 마지막 계산 시각
- `session:timer:{sessionId}` → 타임아웃 스케줄러 상태

> 실제 스키마는 구현 시 확정. 위 구조를 가정하고 코드 설계를 진행하면 빠르게 정합성을 맞출 수 있다.

## 3. API 흐름 상세

1. **세션 시작 (4.1)**
   - 요청: `POST /api/v1/sessions`
   - 필수 헤더: `Idempotency-Key`
   - 검증: 사용자당 ACTIVE 상태 중복 금지 → DB unique 조건 또는 서비스 레벨 체크
   - 응답: 세션 ID, 초기 상태(STARTED), TTL 정보

2. **위치 수신 (4.2)**
   - 요청: `POST /api/v1/sessions/{id}/positions`
   - 저장: 즉시 DB에 기록 + GeoFenceEvaluator 호출
   - 응답: 누적 dwell, 현재 상태

3. **도착 판정 (4.3, 4.4)**
   - GeoFenceEvaluator 로직
     - 입력: 위치 이벤트, 세션 설정(반경 30m, dwell 3분)
     - 상태 기계: `ACTIVE` 상태일 때만 dwell 계산
     - 정확도 >30m 이면 `pause` 플래그로 dwell 누적 중단
     - 반경 밖 이동 시 재진입 타이머 10초 유지

4. **수동 도착 (4.5)**
   - 요청: `POST /api/v1/sessions/{id}/arrivals`
   - 검증: 반경 ≤30m, 시작 후 10~60분 사이
   - 응답: ARRIVED 상태와 도착 시각

5. **타임아웃 (4.6)**
   - 메커니즘: 세션 시작 시 30분 타임아웃 스케줄 예약
   - 구현 후보: Spring `@Scheduled`, Redis 기반 딜레이드 태스크, 또는 Quartz
   - 타임아웃 시 상태를 `EXPIRED`로 전환하고 이벤트 로그 기록

6. **이벤트 로깅 (4.7)**
   - 이벤트 항목: ARRIVED, EXPIRED, CANCELLED
   - 로그 필드: `sessionId`, `userId`, `placeId`, `event`, `timestamp`, `meta`
   - 필요 시 Kafka 연동 준비(현재는 DB/로깅 시스템에 저장)

7. **경계 테스트 (4.8)**
   - 29m/2.9m → 도착 불가
   - 30m/3.0m → 정상 도착
   - 이탈 9초 → dwell 유지, 11초 → 초기화
   - 29분대 도착 허용, 30분 이후 자동 만료

## 4. 기술 스택 체크리스트

- **Spring Boot** 3.5.x
- **JPA** 또는 MyBatis: 세션/위치 테이블 접근 방식 선택 (기존 코드와 일관성 유지)
- **Redis**: dwell 상태, 멱등, 타임아웃 큐에 사용
- **Bucket4j** 레이트리밋: 위치 API 과도 호출 방지 (4.x에는 직접 연동 없음, 참고)
- **Testcontainers**: PostGIS + Redis 환경 통합 테스트 준비

## 5. 공통 모듈 의존성

- `IdempotencyStore`: 세션 시작/도착 멱등 처리
- `RateLimitFilter`: `/sessions` 엔드포인트에 적용되는지 여부 확인 필요
- `ApiResponse`: 공통 응답 포맷 유지
- `ErrorCode`: 세션 관련 신규 코드 추가 예정 (예: SESSION_ALREADY_ACTIVE, SESSION_NOT_FOUND)

## 6. 작업 전 준비 절차

1. **도메인 읽기**
   - `docs/RUA/session-concept-guide.md`
   - 본 가이드 문서
2. **데이터 상태 확인**
   - 로컬 DB에 `visit_sessions`, `visit_positions` 존재 여부
   - 필요한 경우 마이그레이션 스크립트 작성
3. **환경 변수**
   - `SPRING_PROFILES_ACTIVE=test` 로 테스트 실행 (Testcontainers 이용)
   - Redis/PostGIS 컨테이너 기동
4. **테스트 템플릿**
   - `@DataJpaTest` 또는 `@SpringBootTest` 기반 통합 테스트 초안 준비
   - GeoFenceEvaluator 단위 테스트용 Fixture 생성

## 7. 개발 체크리스트(서브 태스크별)

### 4.1 세션 시작
- [ ] DTO 및 요청 검증 구현
- [ ] 멱등키 검사 및 저장 (`IdempotencyStore` 활용)
- [ ] ACTIVE 중복 세션 차단 로직
- [ ] 성공/에러 응답 코드 문서화

### 4.2 위치 수신
- [ ] 위치 DTO/검증
- [ ] 위치 기록 + GeoFenceEvaluator 호출
- [ ] 응답에 현재 누적 dwell, 상태 포함

### 4.3 GeoFenceEvaluator
- [ ] 거리 계산(`ST_DWithin` 혹은 Haversine) 구현
- [ ] dwell 누적 로직과 유예 타이머
- [ ] 테스트: 반경 경계, dwell 누적/초기화 시나리오

### 4.4 정확도 가드
- [ ] accuracy > 30m인 경우 dwell 중단 플래그 설정
- [ ] 플래그가 켜진 상태에서 반경 재진입 시 처리

### 4.5 수동 도착
- [ ] 요청 파라미터 검증 (시간대, 반경)
- [ ] ARRIVED 상태 업데이트 + 이벤트 로그

### 4.6 타임아웃
- [ ] 타임아웃 스케줄러 및 재시작 전략
- [ ] EXPIRED 상태 업데이트 시 멱등성 보장

### 4.7 이벤트 로깅
- [ ] 이벤트 엔터티/레포지토리 또는 로거 구성
- [ ] ARRIVED/EXPIRED/CANCELLED 기록

### 4.8 테스트
- [ ] 단위 테스트: GeoFenceEvaluator, 서비스 로직
- [ ] 통합 테스트: 세션 시작 → 위치 → 도착/만료 흐름

## 8. 참고 아키텍처 다이어그램
```
사용자 → (POST /sessions) → SessionService → SessionRepository
      ↳ (POST /sessions/{id}/positions) → PositionService → GeoFenceEvaluator → Redis dwell cache
      ↳ (POST /sessions/{id}/arrivals) → ArrivalService → EventLogger
      ↳ (Scheduler) → TimeoutService → SessionRepository
```

## 9. 학습 후 확인 질문
- 세션이 ACTIVE 상태에서 도착/취소/만료로 전환되는 조건은?
- dwell 계산에서 정확도 35m 위치 이벤트가 들어오면 어떻게 처리해야 하는가?
- 동일 사용자가 같은 장소에 대해 연속으로 세션을 시작하면 어떤 에러 코드를 반환해야 하는가?
- 타임아웃 스케줄 실패 시 재시도 전략은 무엇인가?

위 질문에 답할 수 있다면 4.x 태스크를 진행할 준비가 완료된 것이다.
