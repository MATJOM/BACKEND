# 4.4 위치 정확도 가드 구현 요약

## 구현 개요
- `DefaultGeoFenceEvaluator`에 정확도 임계값(30m) 체크를 추가해 accuracy가 높은 이벤트에 대해 dwell 증가를 막고 `accuracyPaused=true`를 반환하도록 수정했다.(`src/main/java/com/matjom/matjom/visit/geofence/DefaultGeoFenceEvaluator.java:20`)
- 정확도 가드가 활성화된 경우에도 마지막 위치 정보는 업데이트되므로 이후 이벤트에서 다시 정확한 위치가 들어오면 dwell을 계속 누적할 수 있다.(`src/main/java/com/matjom/matjom/visit/service/VisitPositionService.java:55`)
- `Visit` 엔터티에는 dwell 유지/초기화를 위한 보조 메서드가 이미 존재하며, 정확도 가드는 evaluator 내부에서 이를 적절히 사용하는 방식이다.(`src/main/java/com/matjom/matjom/visit/entity/Visit.java:161`)
- OpenAPI 스펙에 `accuracyPaused` 필드와 세션 위치 응답 스키마를 정의해 계약서를 최신화했다.(`docs/openapi/openapi-v1.yaml:200`)

## 동작 흐름
1. 위치 이벤트의 `accuracyMeters`가 30m 이하이면 기존 dwell 로직과 동일하게 동작한다.
2. accuracy가 30m 초과면
   - `visit.startDwellIfAbsent`는 호출하지 않는다.
   - 현재 dwell이 진행 중이라도 증가시키지 않고, 결과의 dwellSeconds는 이전 값으로 유지된다.
   - `GeoFenceEvaluationResult`에 `accuracyPaused=true`을 설정한다.
3. 정확도가 개선되어 30m 이하 이벤트가 들어오면 다시 dwell이 증가한다.

## 테스트
- 정확도 50m 이벤트에서 dwell 정지가 일어나는지, 정확도 회복 후 재개되는지 검증.(`src/test/java/com/matjom/matjom/visit/geofence/DefaultGeoFenceEvaluatorTest.java:63`)
- 유예 시간 내 이탈/복귀와 정확도 가드가 함께 동작할 때 dwell 유지가 보장되는지 검증.(`src/test/java/com/matjom/matjom/visit/geofence/DefaultGeoFenceEvaluatorTest.java:27`)

## 후속 TODO
- 정확도 가드 발생 시점에 대한 로그 및 메트릭 계측을 추가해 운영 관측성을 확보한다.
