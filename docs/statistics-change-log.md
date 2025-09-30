# Statistics 패키지 구현/테스트 정리 (2025-09-30)

## UC-Stat-01: `GET /api/places/{placeId}/stats`
- DTO/스냅샷: `PlaceStatsResponseDTO`, `PlaceStatsSnapshot`을 통해 누적 방문/좋아요/11~20시 시간대별 평균 도착 수를 전달한다.
- 저장소: `PlaceStatisticsRepository` 네이티브 쿼리로 `visits`/`daily_likes`를 집계하며, 11~20시 모든 시간대의 최근 14일(당일 포함) 평균 도착 인원을 계산한다.
- 서비스: `PlaceStatisticsService`가 장소 존재 여부를 확인한 뒤 DB 스냅샷을 계산해 그대로 응답한다 (캐시 사용 없음).
- 컨트롤러: `PlaceStatisticsController`에 `GET /api/places/{placeId}/stats` 엔드포인트 추가.
- 테스트: `PlaceStatisticsServiceTest`, `PlaceStatisticsControllerTest`로 서비스/컨트롤러 로직 검증.

## UC-Batch-01: 자정 집계
- 저장소: `PlaceDailyStatsBatchRepository`가 `visits/reviews/daily_likes` 기반 집계를 수행하고 `place_daily_stats`에 업서트(peak_hour 필드는 더 이상 기록하지 않음).
- 서비스: `DailyStatsBatchService`가 집계를 실행해 `place_daily_stats`를 최신화한다.
- 스케줄러/컨트롤러: `StatisticsSchedulingConfig`, `DailyStatsBatchScheduler`(00:00 KST), `DailyStatsBatchController`(`POST /api/batch/midnight-reset`, `GET /api/batch/status/last`).
- 테스트: `DailyStatsBatchServiceTest`, `DailyStatsBatchSchedulerTest`, `DailyStatsBatchControllerTest`로 집계·스케줄·API 동작을 검증.
- 문서: `docs/uc-batch-01-midnight-reset.md`에 집계 흐름, API 응답, 모니터링 지표를 정리.

## 기타 보완 사항
- 테스트에서 Spring Security 필터를 비활성화(`@AutoConfigureMockMvc(addFilters = false)`)해 인증 실패를 방지.
- `tasks/statistics-task-plan.md`에 실행된 작업/테스트/모니터링 정의를 최신화했고, 향후 수행할 통합 테스트 계획을 명시.
- 장소 상세 조합 API를 위해 `placefacade` 패키지를 추가하고 `PlaceDetailFacadeServiceTest`, `PlaceDetailControllerTest`를 신규 작성.

## 현재 통계 패키지 테스트 목록 (2025-09-28 기준)
- `statistics.controller`
  - `PlaceStatisticsControllerTest`
  - `DailyStatsBatchControllerTest`
- `statistics.service`
  - `PlaceStatisticsServiceTest`
  - `DailyStatsBatchServiceTest`
  - `DailyStatsBatchSchedulerTest`

위 테스트 모두 `./gradlew test`로 성공하며, 통계 패키지에서 추가한 REST API/배치 로직을 커버합니다. 향후 Postgres 실환경에서의 통합 테스트는 별도의 TODO로 남겨 두었습니다.
