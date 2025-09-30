# Statistics Feature Task Plan

## UC-Stat-01: 음식점 종합 통계 조회
- [x] 요구사항 확정
  - [x] 응답 필드(누적 방문·좋아요·시간대 평균) 정의 (9월 26일 최종)
    - `docs/uc-stat-01-api.md`에 필드 테이블 추가, `PlaceStatsResponseDTO` 구조와 일치.
  - [x] 시간대 지표 평균 기간 확정 (9월 30일 최종)
    - 11~20시 각 시간대 값은 최근 14일(당일 포함) 도착 인원 평균으로 노출.
  - [x] 통계 조회는 캐시 없이 DB 스냅샷을 직접 반환하도록 결정 (2025-09-30 개편)
    - 값은 자정 배치로만 변하므로 매 요청 DB 조회 비용이 허용 가능하다고 판단.
- [x] 조회/집계 쿼리 초안 (9월 26일 최종)
  ```sql
  -- 누적 방문자/좋아요 + 11~20시 시간대별 최근 14일 평균 (2025-09-30 개편)
  SELECT
      (SELECT COUNT(*)
       FROM visits v_total
       WHERE v_total.place_id = :placeId
         AND v_total.arrived_at IS NOT NULL) AS total_visitors,
      (SELECT COUNT(*)
       FROM daily_likes dl
       WHERE dl.place_id = :placeId
         AND dl.status = 'ACTIVE') AS total_likes;

  SELECT hour_key AS hour,
         CAST(COALESCE(ROUND(COUNT(v_hour.id)::numeric / 14, 0), 0) AS bigint) AS average_arrivals
  FROM generate_series(11, 20) AS hour_key
  LEFT JOIN visits v_hour
    ON v_hour.place_id = :placeId
   AND v_hour.arrived_at IS NOT NULL
   AND DATE(v_hour.arrived_at AT TIME ZONE 'Asia/Seoul') BETWEEN CURRENT_DATE - INTERVAL '13 day' AND CURRENT_DATE
   AND EXTRACT(HOUR FROM (v_hour.arrived_at AT TIME ZONE 'Asia/Seoul')) = hour_key
  GROUP BY hour_key
  ORDER BY hour_key;
  ```
- [x] 데이터/인프라 점검
  - [x] `visits` 인덱스 및 상태 값 확인 (9월 26일 최종)
    - `schema-postgres.sql` 기준으로 `idx_visits_user/place/state/started_at` 확인, 상태는 `ACTIVE/ARRIVED/EXPIRED/CANCELLED`로 제한.
  - [ ] (보류) Redis 도입 여부는 추후 트래픽 증가 시 재검토.
- [x] 서비스/컨트롤러 구현
  - [x] 통계 조회 Service (DB 스냅샷 반환) (9월 26일 최종 / 2025-09-30 개편)
  - [x] `GET /api/places/{placeId}/stats` Controller 및 유효성 검사 (9월 26일 최종)
- [ ] DTO/응답 설계
  - [x] `PlaceStatsResponseDTO` (9월 29일 최종)
    - 응답 필드에서 `placeId` 대신 `placeName`을 노출하도록 수정.
    - `generatedAt`/`cacheTtlSeconds`/`dataSource`는 `@JsonIgnore` 처리해 응답에는 숨기고 내부 로깅에만 활용.
  - [ ] 오류 응답 규격 점검
- [x] 테스트
  - [x] 단위 테스트: 장소 검증·스냅샷 계산 흐름 (9월 26일 최종)
  - [ ] 통합 테스트: 임시 데이터로 카운트 검증 (Postgres 환경 준비 후 진행 예정).
- [x] 문서화
  - [x] API 스펙 및 예시 응답 정리 (9월 26일 최종)
    - `docs/uc-stat-01-api.md`에 엔드포인트, 응답 예시, 캐시 전략을 기록.
  - [x] Task 리스트 업데이트 (9월 26일 최종)
    - `tasks/review-like-summary.md` 12절에 UC-Stat-01 진행 기록 추가, 상위 플랜 체크리스트 갱신.

## UC-Stat-02: 실시간 방문 현황
- (Deprecated) 실시간 체류 인원 응답은 신뢰성 문제로 제거되었습니다. 향후 필요하면 일자 기반 평균 지표로 재설계할 예정입니다.

## UC-Batch-01: 일일 자정 리셋 배치
- [ ] 배치 설계
  - [x] Cron 스케줄(00:00 KST) 설정/재시도 전략 (9월 26일 최종)
    - 스케줄: `0 0 * * * Asia/Seoul` (`@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")`).
    - 실행 구조: 배치 진입점 → `@Transactional` 서비스 호출로 UPSERT 처리, 통계 계산은 단일 트랜잭션 안에서 수행.
    - 재시도: 3회까지 고정 backoff(10초)로 재시도, 모두 실패 시 마지막 예외 로그 + 슬랙 알림 트리거.
    - 중복 실행 방지: (TODO) 분산 락 구현 방식을 추후 결정.
  - [x] 실패 알람/로그 정책 (9월 26일 최종)
    - 표준 로거에 `INFO`(시작/종료), `WARN`(재시도), `ERROR`(최종 실패) 기록.
    - 최종 실패 시 `BatchFailureEvent` 발행 → Slack/Webhook 리스너가 알람 발송.
    - 성공 시 `place_daily_stats.last_aggregated_at` 갱신으로 최종 실행 시각을 API(`GET /api/batch/status/last`)에서 조회 가능.
- [x] 설계 메모 추가 (9월 26일 최종)
  - 집계 순서: 방문 → 리뷰 → 좋아요 → 시간대별 통계.
  - 트랜잭션 범위: 장소별 루프 내에서 트랜잭션 처리, 장소 수가 많을 경우 페이징/배치 크기 조절 필요.
  - 예외 흐름: 재시도 실패 후 이전 성공 데이터가 존재하면 캐시 무효화 없이 유지.
- [x] 조회/집계 쿼리 초안 (9월 26일 최종)
  ```sql
  -- 전일(어제) 방문 요약
  WITH yesterday AS (
      SELECT CURRENT_DATE - INTERVAL '1 day' AS target_date
  )
  SELECT
      y.target_date,
      v.place_id,
      COUNT(*) FILTER (WHERE v.state = 'ACTIVE' AND v.started_at::date = y.target_date) AS starts,
      COUNT(*) FILTER (WHERE v.arrived_at IS NOT NULL AND v.arrived_at::date = y.target_date) AS arrives,
      COUNT(r.id) FILTER (WHERE r.created_at::date = y.target_date)                           AS reviews,
      COUNT(dl.id) FILTER (WHERE dl.date_kst = y.target_date AND dl.status = 'ACTIVE')        AS likes
  FROM yesterday y
  LEFT JOIN visits v ON v.started_at::date = y.target_date OR v.arrived_at::date = y.target_date
  LEFT JOIN reviews r ON r.place_id = v.place_id AND r.created_at::date = y.target_date
  LEFT JOIN daily_likes dl ON dl.place_id = v.place_id AND dl.date_kst = y.target_date
  WHERE v.place_id = :placeId
  GROUP BY y.target_date, v.place_id;

  -- UPSERT into place_daily_stats
  INSERT INTO place_daily_stats (date_kst, place_id, starts, arrives, reviews, likes, hourly_arrives, hourly_starts)
  VALUES (...)
  ON CONFLICT (date_kst, place_id)
  DO UPDATE SET
      starts = EXCLUDED.starts,
      arrives = EXCLUDED.arrives,
      reviews = EXCLUDED.reviews,
      likes = EXCLUDED.likes,
      hourly_arrives = EXCLUDED.hourly_arrives,
      hourly_starts = EXCLUDED.hourly_starts,
      updated_at = now(),
      last_aggregated_at = now();
  ```
- [x] 데이터 집계 로직 (9월 26일 최종)
  - [x] `visits`/`reviews`/`daily_likes`를 이용한 전일 통계 계산 쿼리
  - [x] `place_daily_stats` UPSERT 구현
- [x] 배치 API/상태 조회 (9월 26일 최종)
  - [x] `POST /api/batch/midnight-reset` 내부 엔드포인트
  - [x] `GET /api/batch/status/last` 최근 실행 결과 조회
- [ ] 시간대 통계/예측 재학습
  - [x] 시간대별 통계 계산 (9월 26일 최종)
    - `DailyStatsBatchService`가 시간대별 지도(JSON)까지 집계하여 `place_daily_stats`에 저장.
  - [x] 예측 모델 재학습 트리거 정의 (9월 26일 최종)
    - `DailyStatsPredictionService.scheduleRetraining`으로 배치 결과 기반 재학습 훅 연결.
- [ ] 테스트
  - [x] 단위 테스트: 집계 로직 (9월 26일 최종)
  - [ ] 통합 테스트: 배치 실행 후 통계 업데이트 확인 (향후 Postgres 환경에서 수행 예정)
  - [x] Scheduler 동작 검증(Mock 또는 실제 Trigger) (9월 26일 최종)
- [x] 문서/운영 (9월 26일 최종)
  - [x] 배치 운영 가이드 및 재실행 절차 문서화 (`docs/uc-batch-01-midnight-reset.md`, `docs/statistics-change-log.md`)
  - [x] 모니터링 지표 정의 (9월 26일 최종)
    - 기본 지표: 마지막 성공 시각(`place_daily_stats.last_aggregated_at`), 처리한 장소 수(`processedPlaces`), 배치 실행 소요 시간.
    - 실패 모니터링: 예외 메시지/스택 로그 + 슬랙 알림, 캐시 무효화 실패 카운트.

## 공통 마무리
- [x] 전체 `./gradlew test` 통과 확인 (9월 28일 최종)
- [ ] README/summary 문서 업데이트
- [ ] Task 진행 상황을 `tasks/review-like-summary.md` 등 문서에 반영

## Place Detail Facade (9월 30일 신규)
- [x] 통합 응답 DTO 정의 (`PlaceDetailResponseDTO`) — `statistics` + `reviews` + `totalReviewCount` 묶음
- [x] `PlaceDetailFacadeService`에서 통계/리뷰 서비스 조합, 기본 리뷰 노출 5건 제한
- [x] `PlaceDetailController` (`GET /api/places/{placeId}/detail`) 추가, `reviewLimit` 쿼리 파라미터로 조절
- [x] 단위 테스트: `PlaceDetailFacadeServiceTest` (limit 적용/리뷰 수 검증)
- [x] 컨트롤러 테스트: `PlaceDetailControllerTest` (응답 구조 확인)
