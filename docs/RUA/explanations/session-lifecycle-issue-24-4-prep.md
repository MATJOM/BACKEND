# 4.4 위치 정확도 가드 사전 학습 가이드 (주니어용)

## 1. 목표 요약
GPS 정확도가 **30m 초과**인 위치 이벤트는 dwell 계산에서 제외하거나 일시 중단하도록 로직을 구현한다. 이를 통해 잘못된 도착 판정을 방지한다.

## 2. 선행 지식
| 항목 | 설명 |
| --- | --- |
| accuracyMeters | 위치 이벤트의 오차 범위(미터). 값이 클수록 신뢰도가 낮음. |
| GeoFenceEvaluator | 4.3에서 구현한 도착 판정 로직. 정확도 가드 로직은 여기에서 `accuracyPaused` 판단을 추가한다. |
| Visit 엔터티 | `startDwellIfAbsent`, `resetDwell`, `updateLastPosition` 등이 구현되어 있어 dwell 조작이 가능하다. |
| VisitPositionService | evaluator의 결과를 받아 응답으로 전달한다. `accuracyPaused`가 true일 경우 클라이언트에 전달되어 UI 처리 가능. |

## 3. 구현 포인트
1. **Threshold**: accuracyMeters > 30.0이면 dwell을 증가시키지 않고 `accuracyPaused = true`로 반환해야 한다.
2. **누적 로직**
   - 정확도가 낮은 위치 이벤트는 `visit.startDwellIfAbsent`를 호출하지 않는다.
   - 이미 dwell이 진행 중이었더라도 정확도가 낮으면 누적 시간을 유지하되 증가시키지 않는다.
   - 정확도가 낮은 상태가 일정 시간 지속되더라도 dwell을 완전히 리셋하지는 않는다.(추후 정책에 따라 조정 가능)
3. **응답 필드**: `VisitPositionResponse.accuracyPaused`를 true로 설정하면 프런트에서 사용자를 안내할 수 있다.

## 4. 구현 순서 제안
1. `DefaultGeoFenceEvaluator`에 accuracy 값을 검사하는 조건 추가.
2. accuracy가 null이거나 0이면 정상 위치로 처리.
3. accuracy > 30m인 경우:
   - dwellStartedAt 변경 없음.
   - dwellSeconds는 이전 값 유지 (갱신하지 않음).
   - `accuracyPaused` true 반환.
   - 상태 전이는 하지 않는다.
4. 테스트 케이스 작성:
   - accuracy=50m인 이벤트가 들어오면 dwellSeconds 증가 없음 + accuracyPaused=true.
   - accuracy가 다시 5m로 줄어들면 dwell이 정상적으로 재개된다.

## 5. 주의 사항
- 입력 DTO에서 accuracyMeters는 null 허용. null인 경우 정확도 정보를 알 수 없으므로 일반 위치로 간주.
- 정확도 가드가 활성화된 상태에서도 `Visit.updateLastPosition`은 호출하여 나중에 비교가 가능하도록 한다.
- 정책이 변경될 수 있으므로 정확도 임계값(30m)은 상수로 관리.

이 문서를 숙지하면 4.4 위치 정확도 가드를 구현할 준비가 완료된다.
