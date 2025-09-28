# Statistics 패키지 구현/테스트 정리 (2025-09-30)

## UC-Stat-01: `GET /api/places/{placeId}/stats`
- DTO/스냅샷: `PlaceStatsResponseDTO`, `PlaceStatsSnapshot`, `StatsDataSource`를 통해 누적 방문/좋아요/특정 시간대 도착 수를 전달하며, 메타데이터(`generatedAt`, `cacheTtlSeconds`, `dataSource`)는 `@JsonIgnore` 처리로 응답에서 숨기고 내부 로깅에만 사용.
- 저장소: `PlaceStatisticsRepository` 네이티브 쿼리로 `visits`/`daily_likes`를 집계하며, 11~12시·12~13시 도착 인원은 최근 14일(당일 포함) 평균으로 산출.
- 서비스: `PlaceStatisticsService`는 장소 존재 여부를 확인한 뒤 스냅샷을 생성하고, `PlaceStatisticsQueryService`가 Redis(`PlaceStatsCacheService`)와 Cache-aside 패턴으로 연결.
- 컨트롤러: `PlaceStatisticsController`에 `GET /api/places/{placeId}/stats` 엔드포인트 추가.
- 테스트: `PlaceStatisticsServiceTest`, `PlaceStatisticsQueryServiceTest`, `PlaceStatisticsControllerTest`로 서비스/캐시/컨트롤러 로직 검증.

## UC-Batch-01: 자정 집계 및 캐시 무효화
- 저장소: `PlaceDailyStatsBatchRepository`가 `visits/reviews/daily_likes` 기반 집계를 수행하고 `place_daily_stats`에 업서트.
- 서비스: `DailyStatsBatchService`가 집계 실행, 캐시 무효화(`PlaceStatsCacheService`) 및 예측 재학습 훅(`DailyStatsPredictionService.scheduleRetraining`) 호출.
- 예측 Stub: `DailyStatsPredictionService`가 `statistics.prediction.enabled/endpoint` 설정에 따라 REST 호출을 수행.
- 스케줄러/컨트롤러: `StatisticsSchedulingConfig`, `DailyStatsBatchScheduler`(00:00 KST), `DailyStatsBatchController`(`POST /api/batch/midnight-reset`, `GET /api/batch/status/last`).
- 테스트: `DailyStatsBatchServiceTest`, `DailyStatsBatchSchedulerTest`, `DailyStatsBatchControllerTest`, `DailyStatsPredictionServiceTest`로 집계·스케줄·API·예측 트리거를 검증.
- 문서: `docs/uc-batch-01-midnight-reset.md`에 집계 흐름, API 응답, 모니터링 지표, 예측 재학습 트리거 명시.

## 기타 보완 사항
- 로컬 프로필(`application-local.yml`)에 Redis 호스트/포트를 추가해 통계 캐시 환경을 명시.
- 테스트에서 Spring Security 필터를 비활성화(`@AutoConfigureMockMvc(addFilters = false)`)해 인증 실패를 방지.
- JavaTime 직렬화를 위해 `DailyStatsPredictionServiceTest`에서 `ObjectMapper.findAndRegisterModules()` 사용.
- `tasks/statistics-task-plan.md`에 실행된 작업/테스트/모니터링 정의를 최신화했고, 향후 수행할 통합 테스트 계획을 명시.
- 장소 상세 조합 API를 위해 `placefacade` 패키지를 추가하고 `PlaceDetailFacadeServiceTest`, `PlaceDetailControllerTest`를 신규 작성.

## 현재 통계 패키지 테스트 목록 (2025-09-28 기준)
- `statistics.controller`
  - `PlaceStatisticsControllerTest`
  - `DailyStatsBatchControllerTest`
- `statistics.service`
  - `PlaceStatisticsServiceTest`
  - `PlaceStatisticsQueryServiceTest`
  - `DailyStatsBatchServiceTest`
  - `DailyStatsBatchSchedulerTest`
  - `DailyStatsPredictionServiceTest`

위 테스트 모두 `./gradlew test`로 성공하며, 통계 패키지에서 추가한 REST API/캐시/배치 로직을 커버합니다. 향후 Postgres 실환경에서의 통합 테스트는 별도의 TODO로 남겨 두었습니다.
