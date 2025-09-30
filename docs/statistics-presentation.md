# Statistics 패키지 이해 가이드 (발표 자료)

> **3인 전문가 패널 논의 요약**
> - **프로덕트 리더**: "왜 필요한가?" 사용자 경험 관점에서 방문/리뷰 데이터를 한눈에 제공해야 한다.
> - **데이터 엔지니어**: "어떻게 계산할까?" 방문·리뷰·좋아요 테이블을 정규화해 실시간/배치 통계를 구성하자.
> - **플랫폼 엔지니어**: "운영은 안전한가?" 자정 배치로 데이터 일관성을 유지하고, 필요 시 캐시를 붙일 수 있게 설계를 준비하자.

> **NOTE (2025-09-30)** 현재 구현은 Redis 캐시를 사용하지 않고 통계를 DB에서 직접 조회합니다. 아래에 남아 있는 캐시 관련 설명은 과거 설계를 참고용으로 보존한 것이며, 향후 트래픽 확장 시 다시 활성화할 수 있습니다.

---

## 1. 문제 정의와 목표

- **문제 상황**
  - 방문자는 "지금 매장이 얼마나 붐비는지", "후기가 어떤지"를 빠르게 확인하고 싶다.
  - 운영팀은 매일 쌓이는 방문/리뷰 데이터를 집계해 통계와 예측 모델에 활용해야 한다.

- **Statistics 패키지의 목표**
  1. **실시간 요약 제공**: (`GET /api/places/{placeId}/stats`) 누적 방문·좋아요·시간대 통계를 즉시 제공.
  2. **데이터 신뢰성 유지**: 시간대 지표를 최근 14일 평균으로 계산해 단발성 변동을 줄임.
  3. **배치 집계**: 자정마다 전일 데이터를 집계해 다음날 조회가 빠르게 이뤄지도록 준비 (캐시는 현재 사용하지 않음).
  4. **장소 상세 조합 제공**: 통계와 최신 리뷰를 묶어 프론트엔드가 한 번에 활용할 수 있는 Facade API를 마련.

- **최종 기대 효과**
  - 사용자: 앱에서 "지금 가도 되는가"를 빠르게 판단.
  - 운영팀: 하루 단위 집계가 자동화되어 모니터링에 집중.

---

## 2. 로직 흐름 한눈에 보기

| 단계 | 요청/작업 | 주요 컴포넌트 | 설명 |
| --- | --- | --- | --- |
| A | 사용자 API 호출 | `PlaceStatisticsController` | 누적 방문/좋아요 + 특정 시간대 도착 수를 요약 |
| B | 자정 배치 | `DailyStatsBatchScheduler/Service` | 전일 데이터를 집계해 `place_daily_stats`를 최신화 |
| C | 장소 상세 조합 | `PlaceDetailFacadeService/Controller` | 통계 + 리뷰 데이터를 묶어 단일 응답으로 제공 |

---

## 3. 실시간 통계 API (`GET /api/places/{placeId}/stats`)

### 왜 필요할까?
- 앱 진입 시 "리뷰가 몇 개?", "최근 방문이 얼마나 활발했는지?"를 빠르게 확인해 탐색 시간을 줄임.

### 구현 체계
- **데이터 조회**: `PlaceStatisticsRepository`가 `visits` / `daily_likes`를 네이티브 쿼리로 직접 집계한다.
- **서비스**: `PlaceStatisticsService`가 장소 존재 여부 확인 후 스냅샷을 생성해 컨트롤러와 Facade에 제공한다.
- **응답 DTO**: `PlaceStatsResponseDTO`는 `placeName`, 누적 방문/좋아요, 최근 14일 평균 특정 시간대(11~12시·12~13시) 도착 카운트만 응답에 노출한다.
- **테스트**: `PlaceStatisticsServiceTest`, `PlaceStatisticsControllerTest`.

### 프론트와의 연계 (Place Detail Facade)
- 장소 상세 화면에서는 통계와 최신 리뷰가 동시에 필요하므로, `PlaceDetailController`가 `PlaceDetailFacadeService`를 통해 두 정보를 묶어 반환합니다.
- Facade는 통계 서비스를 호출해 최신 데이터를 받고, 리뷰 서비스에서 가져온 데이터를 기본 5개만 노출하며 `reviewLimit` 파라미터로 조절합니다.
- 응답 구조(`PlaceDetailResponseDTO`)에는 전체 리뷰 수가 포함되어 있어 프론트에서 추가 페이지 요청 여부를 쉽게 결정할 수 있습니다.

### 흐름 요약
요청이 들어오면 `PlaceStatisticsService`가 곧바로 DB 스냅샷을 계산해 응답한다. 값은 자정 배치에 의해 하루 단위로만 변하므로 캐시가 없어도 부하가 크지 않다.

---

## 4. 자정 배치 (`DailyStatsBatchService`)

### 왜 필요할까?
- 전일 데이터를 정리해 다음날 분석·예측·대시보드의 기초 자료를 제공.
- 캐시를 자정마다 초기화해 데이터 신선도를 보장.

- **집계 저장소**: `PlaceDailyStatsBatchRepository`
  - `visits` 기반 총계 + 시간대별 도착/출발 JSON, `reviews`, `daily_likes` 집계 포함.
- **배치 서비스**: `DailyStatsBatchService`
  - 일별 통계를 업서트하고 배치 상태(`BatchStatus`)를 기록한다.
- **스케줄러**: `DailyStatsBatchScheduler` 자정(00:00 KST) 실행.
- **API**: `DailyStatsBatchController`
  - `POST /api/batch/midnight-reset` : 수동 집계 실행.
  - `GET /api/batch/status/last` : 마지막 실행 결과 확인.
- **테스트**: `DailyStatsBatchServiceTest`, `DailyStatsBatchSchedulerTest`, `DailyStatsBatchControllerTest`, `DailyStatsPredictionServiceTest`.
- **문서**: `docs/uc-batch-01-midnight-reset.md`, `docs/statistics-change-log.md`.

### 모니터링 지표 제안
- 마지막 성공 시각, 처리한 장소 수, 실행 소요 시간.
- 배치 실패 시 Slack 알림.

---

## 6. 테스트 구성 현황

| 범주 | 클래스 | 비고 |
| --- | --- | --- |
| Controller | `PlaceStatisticsControllerTest`, `DailyStatsBatchControllerTest` | Security 필터 제거(`@AutoConfigureMockMvc(addFilters = false)`) |
| Service | `PlaceStatisticsServiceTest`, `DailyStatsBatchServiceTest`, `DailyStatsBatchSchedulerTest`, `DailyStatsPredictionServiceTest` | 예측/배치 로직 검증 |
| 문서 | `docs/statistics-change-log.md`, `docs/uc-batch-01-midnight-reset.md`, `docs/statistics-presentation.md` | 작업 내역/프로세스 공유 |

> *Postgres 환경에서의 통합 테스트는 추후 운영 DB 계층이 준비되면 추가 예정입니다.*

---

## 7. 향후 확장 로드맵

1. **예측 모델 연동 고도화**
   - `statistics.prediction.enabled=true`로 전환, 외부 모델 서비스와 연동.
2. **통합 테스트(Postgres)**
   - 실제 스키마와 동일한 Testcontainers 기반 통합 테스트 추가.
3. **모니터링/알림 강화**
   - 배치 성공/실패 지표를 Prometheus & Slack 연계.
4. **추가 통계 API**
   - 누적 리뷰/좋아요 추이, 사용자별 방문 패턴 등으로 확장.

---

## 8. 발표 시 강조 포인트

1. **Why**: 사용자에게 실시간 의사결정 정보를 주고, 운영팀은 자동 집계/예측 트리거를 얻는다.
2. **How**: 캐시 + 배치 + REST API 세 단계를 통해 안정성·성능·확장성을 확보.
3. **What**: `/stats`, `/batch` API + Redis 캐시 + 자정 배치.
4. **Result**: 테스트와 문서화까지 완료되어, 통계 도메인 전반을 일관된 흐름으로 관리 가능.

---

## 9. 팀 브리핑 요약 (서비스 → 데이터 → API → 사용자 정보)

| 단계 | 설명 |
| --- | --- |
| **서비스** | - 실시간 장소 통계: 누적 방문·좋아요·시간대 도착 수 제공<br>- 일일 집계: 자정 배치로 데이터 요약 및 예측 훅 실행 |
| **데이터** | - `visits`: 상태, 도착시각, 만료/취소 여부 → 방문/시간대 계산<br>- `daily_likes`: 활성 좋아요 → 누적 좋아요 수<br>- `place_daily_stats`: 배치가 작성하는 일일 요약(확장 대비 구조 유지)<br>- `places`: `id`, `name` → 사용자 응답에 장소 이름 제공 |
| **API** | - `GET /api/places/{placeId}/stats` → `PlaceStatisticsService`가 DB 스냅샷을 계산해 공통 응답(`ApiResponse`)으로 반환<br>- `GET /api/places/{placeId}/detail` → Facade(`PlaceDetailFacadeService`)로 통계 + 최신 리뷰를 단일 응답으로 제공<br>- 자정 배치: `DailyStatsBatchService` UPSERT, `DailyStatsBatchController`로 상태 조회/수동 실행 |
| **사용자 정보** | - 즉시 확인: 누적 방문자, 누적 좋아요, 최근 14일 평균 특정 시간대 도착 인원 |

참고 문서 & 테스트: `docs/uc-stat-01-api.md`, `docs/uc-batch-01-midnight-reset.md`, `docs/statistics-change-log.md`, `tasks/review-like-summary.md`, `tasks/statistics-task-plan.md`, `PlaceStatistics*`, `DailyStats*` 단위 테스트, `./gradlew test`.

---

## 10. Repository 쿼리 설계 해설

- **목적**: 통계 API 한 번 호출로 누적 방문(`total_visitors`), 누적 좋아요(`total_likes`), 11~20시 시간대별 최근 14일 평균 도착(`hourly_arrivals`)을 계산해 `PlaceStatsSnapshot`으로 반환.
- **구현 포인트**
  - 네이티브 쿼리 하나에 서브쿼리를 붙여 Postgres가 직접 집계하도록 구성 (`COALESCE` + `ROUND` 조합으로 평균을 정수로 반올림).
  - 시간대별 도착은 `AT TIME ZONE 'Asia/Seoul'`과 `EXTRACT(HOUR …)`를 조합해 KST 기준 11~12시, 12~13시에 해당하는 방문만 집계하고, `DATE BETWEEN (targetDate - 13일) AND targetDate` 범위로 최근 14일을 포괄합니다.
  - 입력 파라미터는 `placeId`, `targetDate`, `currentTimestamp` 뿐이라 서비스에서 날짜 변환 로직이 단순합니다.

### 11.2 `PlaceDailyStatsBatchRepository` (자정 배치 집계)
- **목적**: 일별 집계 배치가 장소별 전일 데이터를 계산해 `place_daily_stats`에 UPSERT 할 수 있도록, 여러 집계/시간대 데이터를 반환합니다.
- **구현 포인트**
  - `COUNT(*) FILTER (WHERE …)` 문법(Postgres 전용)을 활용해 출발·도착을 한 쿼리에서 나눠 세고, `DATE(... AT TIME ZONE 'Asia/Seoul')` 조건으로 KST 기준 전일 값을 보장합니다.
  - 시간대별 집계는 `EXTRACT(HOUR …)`를 INT로 캐스팅해 시간-카운트 쌍을 반환하며, 서비스 계층에서 JSON 문자열로 구성합니다.
  - 리뷰/좋아요 집계는 `status='ACTIVE'` 조건으로 활성 데이터만 포함합니다.
  - UPSERT는 `ON CONFLICT (date_kst, place_id)`로 중복을 처리하고 `CAST(:hourlyArrives AS jsonb)` 형태로 JSONB 필드를 저장합니다.
  - 메서드들은 `List<Object[]>` 또는 `void`만 반환하므로 배치 서비스 로직이 단순해집니다.

### 11.3 `PlaceDailyStatsReadRepository` (패턴 조회)
- **목적**: `place_daily_stats`에서 기간별 시간대 데이터(JSON)를 읽어 UC-Stat-02 패턴/예측 기능(향후 확장)에 활용합니다.
- **구현 포인트**
  - `BETWEEN :startDate AND :endDate` 범위를 DESC 정렬로 가져와 최신 데이터부터 사용 가능.
  - JSON 컬럼은 `Object[]`에서 `value.toString()`으로 꺼내고, NULL 방지를 위해 `{}`로 치환합니다.
  - `DailyPatternRow` record로 날짜·시간대 JSON 스트링을 묶어 서비스에서 바로 가공할 수 있게 했습니다.

### 11.4 복잡성 최소화 전략
- Postgres 기능을 적극 활용(`FILTER`, `AT TIME ZONE`, `COALESCE`, `jsonb` CAST 등)해 Java 후처리를 최소화합니다.
- 한 번의 쿼리에서 필요한 데이터를 모두 가져오므로 서비스 계층에서 반복 계산이나 추가 쿼리가 필요 없습니다.
- `state`, `status='ACTIVE'` 같은 필터를 SQL 단계에서 미리 적용하여 잘못된 데이터가 UI에 섞이지 않도록 합니다.
- 반환값은 `PlaceStatsSnapshot`, `List<Object[]>`, `DailyPatternRow`와 같이 단순 구조로 정의해 후속 로직이 명확합니다.

---

## 11. 서비스 계층 연관 & 로직 흐름

1. **통계 조회 파이프라인**
   - `PlaceStatisticsController` → `PlaceStatisticsService`
   - 서비스는 장소 존재 여부를 검증하고 `PlaceStatisticsRepository`에서 스냅샷을 계산해 DTO로 반환한다.

2. **자정 배치 파이프라인**
   - `DailyStatsBatchScheduler`(00:00 실행) 또는 내부 API가 `DailyStatsBatchService`를 호출.
   - BatchService는 `PlaceDailyStatsBatchRepository`로 전일 `visits/reviews/daily_likes` 데이터를 집계하고 `place_daily_stats`에 UPSERT한다.

전체적으로 하나의 조회 파이프라인과 한 개의 배치 파이프라인이 동일 데이터를 참조하며 동작합니다.

---

## 12. 서비스 계층 7개의 역할(코드와 함께 쉽게 이해하기)

아래 설명은 "주방/웨이터/알람" 같은 비유와 함께 실제 코드 일부를 보여 드립니다. 파일 경로와 줄 번호가 있으니, 필요하면 그대로 열어 보셔도 됩니다.

### 12.1 통계 스냅샷을 생성하는 서비스 — `PlaceStatisticsService`
```java
// src/main/java/com/matjom/matjom/statistics/service/PlaceStatisticsService.java:37-47
@Transactional(readOnly = true)
public PlaceStatsResponseDTO fetchPlaceStats(Long placeId) {
    String placeName = placeReadRepository.findNameById(placeId)
            .orElseThrow(() -> new PlaceException(ErrorCode.PLACE_NOT_FOUND));

    OffsetDateTime now = OffsetDateTime.now(clock);
    PlaceStatsSnapshot snapshot = placeStatisticsRepository.fetchSnapshot(
            placeId,
            now.atZoneSameInstant(STATISTICS_ZONE_ID).toLocalDate()
    );

    return PlaceStatsResponseDTO.of(placeName, snapshot);
}
```
- **설명**: 장소가 존재하는지 확인한 뒤, 데이터베이스에서 필요한 지표를 계산해 DTO로 반환한다. 캐시 없이도 하루 동안 값이 변하지 않으므로 매 요청 직접 조회해도 부담이 크지 않다.

### 12.3 일일 집계를 수행하는 배치 서비스 — `DailyStatsBatchService`
```java
// src/main/java/com/matjom/matjom/statistics/service/DailyStatsBatchService.java:34-90
public BatchResult runAggregation(LocalDate targetDate) {
    runLock.lock();
    try {
        lastStatus.set(BatchStatus.running(targetDate));
        OffsetDateTime aggregatedAt = OffsetDateTime.now(clock);
        Map<Long, Aggregate> aggregates = collectAggregates(targetDate);

        for (Map.Entry<Long, Aggregate> entry : aggregates.entrySet()) {
            Long placeId = entry.getKey();
            Aggregate aggregate = entry.getValue();

            String hourlyArrives = toJson(aggregate.hourlyArrives);
            String hourlyStarts = toJson(aggregate.hourlyStarts);

            batchRepository.upsertDailyStats(
                    targetDate,
                    placeId,
                    aggregate.starts,
                    aggregate.arrives,
                    aggregate.reviews,
                    aggregate.likes,
                    hourlyArrives,
                    hourlyStarts,
                    null,
                    aggregatedAt
            );
        }

        BatchResult result = new BatchResult(targetDate, aggregatedAt, aggregates.size());
        lastStatus.set(BatchStatus.completed(result));
        return result;
    } catch (Exception ex) {
        log.error("Failed to run daily stats aggregation for date {}", targetDate, ex);
        OffsetDateTime failedAt = OffsetDateTime.now(clock);
        lastStatus.set(BatchStatus.failed(targetDate, failedAt, ex.getMessage()));
        throw ex;
    } finally {
        runLock.unlock();
    }
}
```
- **설명**: 전일 방문/리뷰/좋아요 데이터를 조회해 `place_daily_stats` 테이블에 UPSERT하고, 시간대별 통계는 JSON 문자열로 변환하여 저장합니다.
- `runLock`은 중복 실행을 방지하고, `lastStatus`에는 배치 상태를 기록해 API에서 조회할 수 있게 합니다.

### 12.4 배치 실행 스케줄러 — `DailyStatsBatchScheduler`
```java
// src/main/java/com/matjom/matjom/statistics/service/DailyStatsBatchScheduler.java:21-25
@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
public void runMidnightAggregation() {
    LocalDate targetDate = LocalDate.ofInstant(clock.instant(), KST).minusDays(1);
    log.info("Running daily stats aggregation for {}", targetDate);
    batchService.runAggregation(targetDate);
}
```
- **설명**: `@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")` 설정으로 매일 자정(한국 시간)에 자동 실행됩니다. 실행 시점에는 어제 날짜(`minusDays(1)`)를 계산해 배치 서비스에 전달합니다.

### 12.5 예측 모델 연동 서비스 — `DailyStatsPredictionService`
```java
// src/main/java/com/matjom/matjom/statistics/service/DailyStatsPredictionService.java:38-63
public void scheduleRetraining(LocalDate targetDate,
                               Map<Long, DailyStatsBatchService.DailyStatsSummary> summaries) {
    if (!enabled) {
        log.debug("Prediction retraining disabled...");
        return;
    }
    if (summaries.isEmpty()) {
        log.debug("No summaries to sync ...");
        return;
    }

    PredictionRequest payload = new PredictionRequest(targetDate, summaries);

    try {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        byte[] body = objectMapper.writeValueAsBytes(payload);
        HttpEntity<byte[]> request = new HttpEntity<>(body, headers);

        ResponseEntity<Void> response = restTemplate.postForEntity(URI.create(endpoint), request, Void.class);
        log.info("Triggered prediction retraining for {} (status: {})", targetDate, response.getStatusCode());
    } catch (RestClientException ex) {
        log.warn("Failed to trigger prediction retraining for {}: {}", targetDate, ex.getMessage(), ex);
    }
}
```
- **설명**: 수동 실행과 상태 조회 모두 `ApiResponse` 포맷으로 감싸 통일된 응답 구조를 유지합니다.
- **설명**: 설정(`statistics.prediction.enabled`)이 활성화된 경우에만 실행되며, 집계 요약(`summaries`)을 JSON으로 직렬화해 REST POST 요청으로 전송합니다. 실패 시 경고 로그를 남기고, 예외를 전파하지 않아 배치 본연의 흐름을 방해하지 않습니다.

이 5개의 서비스가 함께 돌아가면서, 사용자는 빠르게 최신 통계를 확인할 수 있고, 내부 팀은 밤마다 자동으로 정리된 데이터를 얻게 됩니다.

---

### 12.6 서비스 메소드별 책임 요약
- **`PlaceStatisticsService`** (`src/main/java/com/matjom/matjom/statistics/service/PlaceStatisticsService.java:37-58`)
  - `fetchPlaceStats(Long placeId)`: 장소 존재 여부 확인 → 스냅샷 조회 → DTO 구성까지 한 번에 처리.
- **`PlaceStatisticsService`** (`src/main/java/com/matjom/matjom/statistics/service/PlaceStatisticsService.java:16-41`)
  - `getPlaceStats(Long placeId)`: 캐시 히트/미스/DB 예외 대비를 모두 아우르는 진입점.
  - `evictCache(Long placeId)`: 배치나 운영 툴에서 캐시를 강제로 비울 때 사용.
- **`DailyStatsBatchService`** (`src/main/java/com/matjom/matjom/statistics/service/DailyStatsBatchService.java:31-215`)
  - `runAggregation(LocalDate targetDate)`: 전일 데이터 집계, UPSERT, 캐시 무효화, 예측 훅 호출까지 담당하는 배치 핵심 메소드.
  - `getLastStatus()`: 최근 배치 상태를 외부에 노출.
  - `collectAggregates(LocalDate targetDate)`: 방문·리뷰·좋아요·시간대 데이터를 한 번에 모으는 내부 헬퍼.
  - `toJson(Map<Integer, Long>)`: 시간대 맵을 JSON 문자열로 변환(직렬화 실패 시 `{}` 반환).
  - `Aggregate#resolvePeakHour()/toSummary()`: 장소별 피크 시간 산출·예측 요약 생성.
  - `BatchResult`, `BatchStatus`: 배치 실행 결과/상태를 표현하는 DTO로 컨트롤러 응답에 사용.
  - `DailyStatsSummary`: 예측 서비스에 전달되는 최소 요약 구조.
- **`DailyStatsBatchScheduler`** (`src/main/java/com/matjom/matjom/statistics/service/DailyStatsBatchScheduler.java:17-30`)
  - `runMidnightAggregation()`: 매일 00:00 KST에 자동으로 배치 서비스 호출.
- **`DailyStatsPredictionService`** (`src/main/java/com/matjom/matjom/statistics/service/DailyStatsPredictionService.java:34-69`)
  - `scheduleRetraining(LocalDate, Map<...>)`: 재학습이 활성화된 경우 외부 예측 엔드포인트로 요약 데이터를 전송.

---


## 14. Place Detail Facade (통계 + 리뷰 조합)

| 구성 요소 | 역할 |
| --- | --- |
| `PlaceDetailResponseDTO` | 통계 응답(`PlaceStatsResponseDTO`), 리뷰 리스트(`ReviewResponseDTO`), 전체 리뷰 수를 하나의 DTO로 묶습니다. |
| `PlaceDetailFacadeService` | 통계 서비스(`PlaceStatisticsService`)와 리뷰 서비스(`ReviewService`)를 호출하여 조합하고, 기본으로 최근 5개 리뷰만 반환하며 `reviewLimit` 파라미터로 확장/완전 조회를 조절합니다. |
| `PlaceDetailController` | `GET /api/places/{placeId}/detail` 엔드포인트로 사용자 화면에서 필요한 데이터를 `ApiResponse` 포맷으로 한 번에 반환합니다. |

- 프론트엔드는 단일 호출로 장소 통계 + 최신 리뷰를 받으므로 네트워크 비용이 줄어듭니다.
- Facade 계층은 기존 도메인 구조를 변경하지 않고 조합만 담당하므로, 리뷰/통계 패키지의 책임 분리는 그대로 유지됩니다.
- 단위/컨트롤러 테스트(`PlaceDetailFacadeServiceTest`, `PlaceDetailControllerTest`)로 응답 구조와 제한 로직을 검증했습니다.

---

## 15. 컨트롤러 계층 분석

### 15.1 `PlaceStatisticsController`
```java
// src/main/java/com/matjom/matjom/statistics/controller/PlaceStatisticsController.java:14-27
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/places/{placeId}/stats")
public class PlaceStatisticsController {

    private final PlaceStatisticsService statisticsQueryService;

    @GetMapping
    public ResponseEntity<PlaceStatsResponseDTO> getPlaceStatistics(@PathVariable @Positive Long placeId) {
        PlaceStatsResponseDTO response = statisticsQueryService.getPlaceStats(placeId);
        return ResponseEntity.ok(response);
    }
}
```
- **설명**: 수동 실행과 상태 조회 모두 `ApiResponse` 포맷으로 감싸 통일된 응답 구조를 유지합니다.
- **필요성**: 통계 데이터는 화면에서 수시로 조회되므로, GET 엔드포인트가 가장 직관적인 접근 방식입니다.
- **구조 이유**: 컨트롤러는 `placeId` 검증(`@Positive`)과 HTTP 응답 포맷만 담당하며, 캐시/DB 조회 흐름은 전부 `PlaceStatisticsService`에 위임합니다. 이렇게 하면 컨트롤러 코드가 얇고 명확하게 유지됩니다.
- **결과**: URL 규칙(`/api/places/{placeId}/stats`)과 응답 형태(`PlaceStatsResponseDTO`)가 일관되고, 캐시 전략 변경 시에도 컨트롤러를 수정할 필요가 없습니다.

### 15.2 `DailyStatsBatchController`
```java
// src/main/java/com/matjom/matjom/statistics/controller/DailyStatsBatchController.java:16-39
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/batch")
public class DailyStatsBatchController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyStatsBatchService dailyStatsBatchService;
    private final Clock clock;

    @PostMapping("/midnight-reset")
    public ApiResponse<DailyStatsBatchService.BatchResult> runMidnightReset(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.ofInstant(clock.instant(), KST).minusDays(1);
        DailyStatsBatchService.BatchResult result = dailyStatsBatchService.runAggregation(targetDate);
        return ApiResponse.ok(result);
    }

    @GetMapping("/status/last")
    public ApiResponse<DailyStatsBatchService.BatchStatus> getLastStatus() {
        return ApiResponse.ok(dailyStatsBatchService.getLastStatus());
    }
}
```
- **설명**: 수동 실행과 상태 조회 모두 `ApiResponse` 포맷으로 감싸 통일된 응답 구조를 유지합니다.
- **필요성**: 자정 배치는 자동으로 돌지만, 운영자는 "지금 당장 다시 실행"하거나 "마지막 배치가 성공했나"를 확인할 수 있어야 합니다. 이를 위해 수동 실행과 상태 조회 API를 제공합니다.
- **구조 이유**: 배치 서비스가 집계/캐시 무효화/예측 연동 등 무거운 로직을 담당하므로, 컨트롤러는 날짜 파라미터 처리와 HTTP 응답 포맷에 집중합니다. 파라미터가 없으면 어제 날짜를 자동으로 계산하는 것도 컨트롤러에서 처리합니다.
- **결과**: 스케줄러와 수동 실행의 공존이 가능해지며, 운영자는 별도 도구 없이도 REST 호출 한 번으로 배치 현황을 확인할 수 있습니다.
- **자정(00:00 KST) 실행 이유**
  - 통계가 “전날 하루”의 데이터를 다루기 때문에 날짜 경계 직후에 집계하는 것이 자연스럽습니다.
  - 밤 시간대는 사용자 트래픽이 적어 배치 실행 시 실시간 응답에 미치는 영향이 작습니다.
  - 자정마다 캐시를 비우고 다음날 새 데이터를 채우는 운영 패턴이 일관되게 유지됩니다.

# Statistics 패키지 팀 브리핑 (서비스 → 데이터 → API → 사용자 정보 흐름)

## 1. 어떤 서비스를 제공하나?
- **실시간 장소 통계**: 특정 음식점의 누적 방문·좋아요 수와 시간대별 평균 도착 현황을 빠르게 보여줍니다.
- **일일 집계 & 캐시 유지**: 매일 자정 전일 데이터를 집계하고 Redis 캐시를 최신 상태로 동기화합니다.

- `visits` 테이블: 방문 상태(`ACTIVE/ARRIVED`), `arrived_at`, 만료/취소 여부 → 누적 방문자 수 및 시간대별 도착 수 계산에 활용.
- `daily_likes` 테이블: `status='ACTIVE'`인 좋아요 기록 → 누적 좋아요 수 집계.
- `place_daily_stats` 테이블: 배치가 업데이트하는 일별 요약 및 시간대 통계(향후 평균 지표에 활용 예정).
- `places` 테이블: `id`, `name` → 사용자 응답에서 장소 이름 제공.

## 3. 어떤 API를 구현했나?
### 3.1 `GET /api/places/{placeId}/stats`
- 서비스: `PlaceStatisticsService` → 장소 존재 확인 + 스냅샷 조회.
- 응답 필드: `placeName`, `totalVisitors`, `totalLikes`, `hourlyArrivals[].{hour, averageCount}`.
- 내부 메타데이터(`generatedAt`, `cacheTtlSeconds`, `dataSource`)는 `@JsonIgnore`로 숨겨 로깅에만 사용.

### 3.2 일일 집계 배치
- 서비스: `DailyStatsBatchService` → `visits/reviews/daily_likes` 집계 후 `place_daily_stats` 업서트.
- 캐시 무효화: 각 장소에 대해 `place:stats:{placeId}` 삭제.
- 상태 조회/수동 실행: `POST /api/batch/midnight-reset`, `GET /api/batch/status/last`.
- 예측 훅: `DailyStatsPredictionService.scheduleRetraining`(현재는 Stub, 향후 모델 연동용).

## 4. 사용자에게 최종적으로 어떤 정보를 전달하나?
- 앱/웹 화면에서 즉시 확인 가능한 항목
  - **누적 방문자 수**
  - **누적 좋아요 수**
- **최근 14일 평균(11~12시, 12~13시) 도착 인원** — 일일 편차를 줄여 사용자에게 안정적인 혼잡도를 제공
- 관리자/운영용(숨김 메타데이터)
  - 응답 생성 시각, 캐시 TTL, 데이터 출처(DB 또는 캐시)
  - 배치 실행 현황 및 예측 재학습 트리거 결과

## 5. 문서 & 테스트 참고
- 스펙 문서: `docs/uc-stat-01-api.md`, `docs/uc-batch-01-midnight-reset.md`
- 변경 내역: `docs/statistics-change-log.md`, `docs/statistics-presentation.md`, `tasks/review-like-summary.md`, `tasks/statistics-task-plan.md`
- 테스트: `PlaceStatistics*`, `DailyStats*` 패키지 단위 테스트 및 `./gradlew test`
