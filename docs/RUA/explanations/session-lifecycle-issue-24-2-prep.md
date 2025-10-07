# 4.2 위치 수신 API 사전 학습 가이드 (주니어용 0→100)

## 1. 목표 한 줄 요약
`POST /api/v1/sessions/{sessionId}/positions` 엔드포인트를 만들고, 들어온 위치 데이터를 안전하게 저장한 뒤 GeoFenceEvaluator(4.3 예정)로 넘길 준비를 한다.

## 2. 사전에 꼭 알아야 할 핵심 개념
| 구분 | 설명 |
| --- | --- |
| Visit(세션) | `visits` 테이블 레코드. 현재 상태(`VisitState`), 사용자, 장소, 시작 시각 등을 보유한다. |
| VisitPosition | 위치 이벤트를 저장하는 테이블. 위도/경도/정확도/수신 시각과 세션 참조(`visit_id`)를 가진다. |
| ClientMode | 클라이언트 동작 모드(`NAVIGATION`, `IDLE`). 위치 이벤트마다 전달된다. |
| 멱등 | 이미 처리된 요청을 다시 받아도 같은 결과를 돌려주는 것. start API에서 구현 완료. 위치 API는 레이트리밋으로 제어하되 멱등 저장은 필요하지 않다. |
| GeoFenceEvaluator | 반경·dwell 계산을 담당할 도메인 서비스. 4.3에서 실제 로직을 넣을 예정이므로 현재는 인터페이스 혹은 스텁 호출 준비만 한다. |

## 3. 현재 코드 구조 빠르게 이해하기
- `Visit` 엔터티: `src/main/java/com/matjom/matjom/visit/entity/Visit.java`
  - 생성자에서 기본 상태를 `ACTIVE`로 설정하고, `startedAt`을 필수로 받는다.
- `VisitSessionService`: `src/main/java/com/matjom/matjom/visit/service/VisitSessionService.java`
  - 4.1에서 구현한 세션 시작 로직이 들어 있다. 위치 API는 여기서 생성한 세션을 전제로 한다.
- `VisitRepository`: `src/main/java/com/matjom/matjom/visit/repository/VisitRepository.java`
  - `existsByUser_IdAndState` 등 Active 세션 체크용 메서드가 있다.
- 아직 없는 것
  - 위치 DTO, 컨트롤러 메서드, 서비스 메서드, Position 저장용 리포지토리.

## 4. 데이터베이스 구조 되짚기
```
visits (visit_id PK, user_id, place_id, state, client_mode, started_at, ...)
visit_positions (pos_id PK, visit_id FK, lat, lng, accuracy_m, mode, received_at, created_at)
```
- 외래키: `visit_positions.visit_id -> visits.visit_id`
- 인덱스: `visit_positions(visit_id, received_at)`로 시간 순 조회 최적화 가능
- 정밀도: 위도/경도는 decimal(9,6), 정확도는 decimal(6,2)

## 5. API 설계 세부사항
- 엔드포인트: `POST /api/v1/sessions/{sessionId}/positions`
- 헤더: 멱등키 필수 아님, 단 `Content-Type: application/json`
- 요청 본문 필드
  - `latitude` (필수): double 또는 BigDecimal → 범위 체크(-90~90)
  - `longitude` (필수): double → 범위 체크(-180~180)
  - `accuracyMeters` (옵션): 0 이상인 값. 없을 경우 null 저장.
  - `mode` (옵션): 기본값 `NAVIGATION`
  - `recordedAt` (필수): 클라이언트에서 위치 측정 시각(UTC), OffsetDateTime
- 응답 본문 필드 (초기 스텁)
  - `sessionId`
  - `state` (현재 세션 상태)
  - `dwellSeconds` (추후 GeoFenceEvaluator와 연동 시 업데이트, 지금은 0 혹은 null)
  - `accuracyPaused` (정확도 가드 적용 여부, 지금은 false)
  - `receivedAt`

## 6. 예외 규칙 정리
| 케이스 | 에러 코드 | 설명 |
| --- | --- | --- |
| 세션이 존재하지 않음 | `SESSION_NOT_FOUND` | visitRepository.findById가 비어있을 때 |
| 세션이 ACTIVE가 아님 | `SESSION_ALREADY_INACTIVE` | ARRIVED/EXPIRED/CANCELLED 상태인 경우 |
| 입력값 범위 오류 | `INVALID_REQUEST_PARAM` | 위도/경도 범위, recordedAt null 등 |

> *주의*: 4.3에서 GeoFenceEvaluator가 상태를 ARRIVED로 바꾸면, 이후 위치 API 호출 시 `SESSION_ALREADY_INACTIVE`가 내려가야 한다.

## 7. GeoFenceEvaluator 연동 계획 (임시)
- 아직 구현하지 않았으므로 인터페이스/스텁 클래스를 정의해 위치 이벤트를 전달할 수 있도록 한다.
- 입력: 저장된 Visit, 위치 DTO, 이전 dwell 상태(현재는 null)
- 출력: `PositionEvaluationResult`(예: dwellSeconds, shouldArrive 등). 지금은 기본값을 반환하는 Mock으로 둔다.

## 8. 구현 순서 제안
1. **DTO 작성**
   - `VisitPositionRequest`, `VisitPositionResponse`
2. **리포지토리 생성**
   - `VisitPositionRepository extends JpaRepository<VisitPosition, Long>`
3. **서비스 레이어**
   - `VisitPositionService` 또는 `VisitSessionService` 내부 메서드 `recordPosition(...)`
   - 세션 조회 → 상태 검증 → VisitPosition 생성 및 저장 → GeoFenceEvaluator 호출 → 세션 최신 좌표/시각 갱신(옵션)
4. **컨트롤러 메서드 추가**
   - `@PostMapping("/{sessionId}/positions")`
   - 데이터 검증(`@Valid`) → 서비스 호출
5. **테스트**
   - 단위 테스트(Mock 활용): 정상 저장, 세션 없음, 상태 비활성, GeoFenceEvaluator 호출 여부
   - 통합 테스트(선택): Testcontainers로 실제 insert 확인

## 9. 테스트 케이스 템플릿
| 시나리오 | 기대 결과 |
| --- | --- |
| 정상 위치 이벤트 | visit_positions INSERT, 응답 state=ACTIVE |
| 세션 없음 | `SessionException(SESSION_NOT_FOUND)` |
| 세션 상태 ARRIVED | `SessionException(SESSION_ALREADY_INACTIVE)` |
| accuracy<0 | 400 INVALID_REQUEST_PARAM |

## 10. 구현 팁 & 주의 사항
- **람다 금지**: 현재 코드베이스 정책. Mockito Answer 등도 익명 클래스 사용.
- **트랜잭션 범위**: 위치 저장은 짧은 트랜잭션으로 처리, 이후 GeoFenceEvaluator 호출이 오래 걸리면 비동기/별도 고려(현재는 동기).
- **시간대**: `recordedAt`은 요청 값 사용, 서버 수신 시각은 `OffsetDateTime.now(Asia/Seoul)` 등 별도로 기록 가능.
- **Place/User 재조회 불필요**: 세션 시작 때 이미 저장했으므로 위치 API는 Visit만 조회하면 된다.
- **성능**: 초당 2회 정도 위치 이벤트가 들어오는 것을 가정. 나중에 레이트리밋(5.x)에서 보완.

## 11. 완료 체크리스트
- [ ] DTO 및 검증 구현
- [ ] VisitPositionRepository 작성
- [ ] VisitSessionService에 위치 처리 메서드 추가
- [ ] GeoFenceEvaluator 스텁/인터페이스 연결
- [ ] 컨트롤러 엔드포인트 추가
- [ ] 단위 테스트 작성 및 `./gradlew test` 통과
- [ ] PRD(4.2) 체크박스 업데이트 및 문서 갱신

이 문서를 숙지했다면, 주니어 개발자라도 4.2 위치 수신 태스크를 혼자서도 진행할 수 있다.
