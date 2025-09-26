# 4.0 방문 세션 라이프사이클 구현 가이드

세션(start → 위치 이벤트 → 도착/취소/만료) 기능을 구현하기 위한 배경지식과 단계별 작업 지시를 정리했습니다. 백엔드 엔지니어가 0에서 100까지 순차적으로 진행할 수 있도록 구성했습니다.

---

## 1. 왜 필요한가?

- 사용자가 특정 장소에 도착했음을 검증하기 위해 “방문 세션”을 생성하고 위치 이벤트를 추적해야 합니다.
- 세션 상태는 `ACTIVE → ARRIVED` 또는 `EXPIRED/CANCELLED` 등으로 전이되며, 전이 조건(반경 30m, dwell 3분, 유예 10초 등)을 서버가 판단해야 합니다.
- 중복 시작(한 사용자에 ACTIVE 세션 2개) 및 멱등성이 보장되어야 하며, 위치 이벤트는 정확도/시간 조건을 충족할 때만 도착으로 인정됩니다.

---

## 2. 현재 코드 현황

| 구분 | 파일 | 상태 |
| --- | --- | --- |
| 엔터티 | `visit.entity.Visit`, `VisitPosition`, `VisitState` 등 | 기본 필드만 정의, 도메인 메서드 없음 |
| 저장소 | `visit.repository.VisitRepository` | 구현 비어있음 |
| 서비스/컨트롤러 | 없음 | 4.x 단계에서 신설 필요 |
| 공통 로직 | GeoFence, dwell 로직 미구현 | 4.3 이후에서 구현 |

---

## 3. 구현 순서 개요

1. `VisitSessionController` 및 `VisitSessionService` 스켈레톤 생성
2. 4.1: 세션 시작 API (`POST /api/v1/sessions/start` 가칭)
   - 멱등키 필수, 사용자별 ACTIVE 중복 방지
   - 트랜잭션 + UNIQUE (user_id, state='ACTIVE') 제약 고려
3. 4.2~4.4: 위치 이벤트 처리/GeoFenceEvaluator 설계
   - dwell 3분, 반경 30m, 정확도 30m 초과 시 일시정지
4. 4.5: 수동 도착 API (arrivals), 4.6: 타임아웃 스케줄러, 4.7: 이벤트 로깅
5. 4.8: 테스트 (경계값, dwell, 타임아웃 등)

본 가이드에서는 우선 4.1 단계 집중 계획을 세운다.

---

## 4. 4.1 세션 시작 API 설계

### 엔드포인트 제안
- `POST /api/v1/sessions` 또는 `/api/v1/sessions/start`
- 헤더: `Idempotency-Key` (멱등 보장)
- Body: `VisitSessionStartRequest`
  - `userId` (또는 JWT 기반) → 현재는 파라미터로 가정
  - `placeId`
  - `clientMode` (auto/manual)
  - `origin` (선택)

### 처리 절차
1. 헤더 검증 (기존 룰렛과 동일하게 반드시 값 확인)
2. 사용자/장소 유효성 체크 (`UserRepository`, `PlaceRepository` 활용)
3. ACTIVE 세션 존재 여부 확인
   - `Visit` 테이블에서 `user_id` + `state = 'ACTIVE'` 검색
   - 존재 시 409 혹은 기존 세션 정보를 반환(요구사항에 따라 결정)
4. 새 Visit 생성
   - state=ACTIVE, started_at=now, client_mode 설정, meta 초기화
5. 멱등 저장소 연동 (향후 7.x 구현과 연결) → 현재는 TODO 주석
6. 응답: `VisitSessionResponse` (sessionId, state, startedAt 등)

### 트랜잭션/동시성
- `@Transactional` 적용, DB UNIQUE 제약 `(user_id, state)` → `state='ACTIVE'` 부분 인덱스 필요(추후 DDL 점검)
- 동시 요청 대비, UNIQUE 위반 시 잡아서 409로 변환

---

## 5. 필요한 컴포넌트 정리

- DTO: `VisitSessionStartRequest`, `VisitSessionResponse`
- 컨트롤러: `VisitSessionController`
- 서비스: `VisitSessionService`
- 리포지토리: `VisitRepository`에 ACTIVE 세션 조회/저장 메서드 추가
- 예외: `VisitException` 등 도메인별 예외 정의
- 매퍼/팩토리: Visit 엔터티 생성 헬퍼 메서드
- 테스트: `VisitSessionControllerTest`, `VisitSessionServiceTest`

---

## 6. 후속 작업 연결

- 4.2~4.4: 위치 이벤트 처리 및 GeoFenceEvaluator 구현 시 start API에서 저장한 정보( startedAt, clientMode )를 활용
- 4.6: 타임아웃 스케줄러 구현 시 `startedAt + 30m` 기반으로 `EXPIRED` 전이

---

## 7. 참고 커밋 메시지 예시

```bash
feat: add visit session start endpoint
- implement POST /api/v1/sessions start logic with idempotency guard
- prevent duplicate active sessions per user
- add controller/service tests for start flow
- related to PRD 4.1 세션 시작 API
```

---

이 가이드를 기반으로 4.1 구현에 착수한 뒤, 진행 상황에 따라 문서와 태스크 리스트를 계속 업데이트하세요.
