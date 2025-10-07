# 4.3 GeoFenceEvaluator 구현 요약

## 구현 개요
- `DefaultGeoFenceEvaluator`가 반경 30m, dwell 180초, 유예 10초 로직을 적용해 `Visit` 상태를 평가한다.(`src/main/java/com/matjom/matjom/visit/geofence/DefaultGeoFenceEvaluator.java:1`)
- `VisitPositionService`는 evaluator를 호출한 뒤 위치 이벤트를 저장하고 세션의 마지막 위치를 갱신하도록 순서를 조정했다.(`src/main/java/com/matjom/matjom/visit/service/VisitPositionService.java:36`)
- `Visit` 엔터티에 dwell 관리/도착 기록을 위한 보조 메서드를 추가했다.(`src/main/java/com/matjom/matjom/visit/entity/Visit.java:138`)

## 동작 흐름
1. 새 위치 이벤트가 접수되면 evaluator가 이전 좌표(`visit.getLastLatitude()/getLastLongitude()`)와 비교해 직전 상태를 파악한다.
2. 현재 위치가 반경 내라면 `startDwellIfAbsent`로 dwell 시작 시각을 유지한다.
3. 반경 밖이라면 직전 위치도 내부였고 10초 이내인지 검사해 dwell 유지 또는 초기화를 결정한다.
4. dwell 누적 시간이 180초 이상이면 `visit.arriveAt(recordedAt)`를 호출해 `ARRIVED` 상태로 전환하고 도착 시각을 기록한다.
5. 평가 결과는 `GeoFenceEvaluationResult`에 상태·dwellSeconds·accuracyPaused(false)를 담아 반환한다.
6. 이후 서비스가 위치 이벤트를 저장하고 `visit.updateLastPosition`으로 최신 좌표/정확도/수신 시각을 보관한다.

## 테스트
- `DefaultGeoFenceEvaluatorTest`로 180초 도착, 유예 유지/초과 초기화를 검증했다.(`src/test/java/com/matjom/matjom/visit/geofence/DefaultGeoFenceEvaluatorTest.java:18`)
- `VisitPositionServiceTest`는 evaluator를 스텁으로 사용하지만 서비스 흐름(세션 미존재, 상태 비활성, 위치 저장)을 회귀 검증한다.(`src/test/java/com/matjom/matjom/visit/service/VisitPositionServiceTest.java:35`)

## 후속 TODO
- 정확도 가드(4.4) 적용 시 evaluator에서 `accuracyPaused` 값을 true로 설정하도록 확장.
- ARRIVED 시점에 이벤트 로깅/권한 트리거(4.7)와 연동 로직을 VisitPositionService에서 추가할 계획.
