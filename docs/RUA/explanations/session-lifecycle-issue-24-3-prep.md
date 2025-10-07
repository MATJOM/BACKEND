# 4.3 GeoFenceEvaluator 구현 사전 학습 가이드 (주니어용)

## 1. 목표 요약
- 사용자의 위치 이벤트가 들어올 때 반경 **30m** 이내에서 **연속 180초** 체류했는지 판단하고, 10초 유예 로직을 적용해 `ARRIVED` 상태로 전환할 수 있는 GeoFenceEvaluator를 구현한다.

## 2. 알아야 할 도메인 지식
| 항목 | 설명 |
| --- | --- |
| 반경 조건 | 기준 장소(Place)의 위경도와 위치 이벤트의 위경도 사이 거리를 미터 단위로 계산한다. 30m 이하이면 `inside`. |
| dwell | `inside` 상태가 계속 유지되는 시간. 180초 이상이면 `ARRIVED`. |
| 유예(Grace) 10초 | `inside` 상태였다가 잠시 반경 밖으로 나가도 10초 이내에 다시 들어오면 dwell 누적을 유지한다. 10초를 초과하면 초기화. |
| Visit 엔터티 | `lastLatitude`, `lastLongitude`, `lastPositionAt`, `dwellStartedAt`, `state`, `arrivedAt` 등을 가진다. |
| VisitPosition | 위치 이벤트(위도, 경도, 정확도, 모드, recordedAt)를 표현한다. |

## 3. 현재 코드 구조
- `VisitPositionService.recordPosition()`에서 evaluator를 호출한다.
- evaluator는 `Visit`과 `VisitPosition`을 입력으로 받아 dwell과 상태를 판단한다.
- evaluator가 반환하는 `GeoFenceEvaluationResult`에는 결과 상태, dwellSeconds, accuracyPaused가 담긴다.

## 4. 수학/알고리즘
1. **거리 계산**: Haversine 공식을 사용하여 두 점 사이의 직선 거리를 미터 단위로 계산한다.
2. **dwell 관리**
   - `visit.getDwellStartedAt()` 값이 없고 `inside`면 시작 시각을 기록.
   - `inside` 상태가 아닌데 직전 위치가 `inside`였고(하나 전 좌표 비교) 10초 이내면 dwell 유지.
   - 그렇지 않으면 dwell 초기화.
3. **도착 판정**: `dwellStartedAt`이 존재하고 `inside` 상태에서 180초 이상 경과하면 `Visit.arriveAt(recordedAt)` 호출.

## 5. 필요한 보조 메서드
- `Visit.updateLastPosition(...)` : 위치/정확도/시간 기록.
- `Visit.startDwellIfAbsent(...)`, `Visit.resetDwell()`으로 dwell 상태 관리.
- `Visit.transitionTo(...)`, `Visit.arriveAt(...)`로 상태 업데이트.

## 6. 구현 순서 제안
1. `Visit` 엔터티에 dwell/위치 갱신 메서드 추가.
2. `GeoFenceEvaluator` 인터페이스에 맞춰 `DefaultGeoFenceEvaluator` 구현.
3. 거리 계산 헬퍼(Haversine) 작성.
4. 유예 로직: 직전 위치의 inside 여부를 판별하기 위해 `visit.getLastLatitude()`와 `visit.getLastLongitude()` 사용.
5. evaluator 호출 후 `VisitPositionService`가 새로운 위치를 visit에 반영하도록 순서 조정(평가 → 저장 → 방문 위치 업데이트).
6. 테스트 시나리오 작성: 180초 도착, 유예 10초 유지, grace 초과 시 초기화 등.

## 7. 테스트 체크리스트
- [ ] 연속 180초 체류 시 ARRIVED로 변경된다.
- [ ] 10초 이내 이탈은 dwell을 유지한다.
- [ ] 10초 초과 이탈은 dwell이 초기화된다.
- [ ] 세션 상태가 ARRIVED로 변경되면 `arrivedAt`가 기록된다.

## 8. 주의 사항
- **시간 계산**: `Duration.between` 사용 시 음수(시간 역순) 체크 필요.
- **精度**: Haversine 계산에서 double을 사용하지만 30m 기준으로 충분.
- **부작용 순서**: evaluator 호출 전에 `visit.updateLastPosition`을 실행하면 이전 좌표 정보를 잃으므로, 평가 후에 업데이트해야 한다.
- **테스트**: Visit/Place는 Mockito로 스텁하거나 Reflection으로 ID 설정.

문서를 완전히 이해하면 4.3 GeoFenceEvaluator를 혼자 구현할 수 있다.
