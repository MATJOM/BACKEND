# 4.6 타임아웃 스케줄러 구현 요약

## 구현 개요
- `VisitTimeoutScheduler`를 추가해 30분 경과 세션을 주기적으로 조회하고 `EXPIRED` 상태로 전환하도록 했다.(`src/main/java/com/matjom/matjom/visit/service/VisitTimeoutScheduler.java:15`)
- 스케줄 실행을 위해 `@EnableScheduling` 구성과 공용 `Clock` 빈을 도입해 시간 계산을 일관되게 수행한다.(`src/main/java/com/matjom/matjom/common/config/SchedulingConfig.java:5`, `src/main/java/com/matjom/matjom/common/config/ClockConfig.java:5`)
- `VisitRepository`에 타임아웃 후보 조회용 쿼리를 추가하고, `Visit` 엔터티에 `setExpiredAt`/`setMeta` 보조 메서드를 제공했다.(`src/main/java/com/matjom/matjom/visit/repository/VisitRepository.java:16`, `src/main/java/com/matjom/matjom/visit/entity/Visit.java:86`)

## 동작 흐름
1. 스케줄러가 60초 간격으로 실행되며, `startedAt <= now-30분`인 ACTIVE 세션을 배치 단위로 조회한다.
2. 조회된 세션의 상태를 `EXPIRED`로 바꾸고 `expiredAt`을 현재 시각으로 기록한 뒤 저장한다.
3. 배치가 빌 때까지 반복하며, 만료된 개수를 info 로그로 남긴다.

## 테스트
- `VisitTimeoutSchedulerTest`에서 만료 대상이 상태 전환되는지, 후보가 없을 때 저장 동작이 발생하지 않는지 검증했다.(`src/test/java/com/matjom/matjom/visit/service/VisitTimeoutSchedulerTest.java:29`)
- `./gradlew test`

## 후속 TODO
- 멀티 인스턴스 환경에서 중복 실행을 막기 위해 Redis 기반 분산 락을 적용할 여지가 있다.
- 만료 이벤트를 `Visit.meta.history` 또는 별도 이벤트 스트림으로 발행해 운영 감사 용도를 강화할 수 있다.
