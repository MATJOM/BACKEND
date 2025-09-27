# Statistics Feature Task Plan

## UC-Stat-01: 음식점 종합 통계 조회
- [ ] 요구사항 확정
  - [ ] 응답 필드(출발 수/도착 수/현재 방문자, TTL 등) 정의
  - [x] Redis 캐시 키/만료 정책 및 기본값 전략 합의 (9월 26일 최종)
    - 키 패턴: `place:stats:{placeId}` (`GET /api/places/{placeId}/stats`), `place:visit-info:{placeId}` (`GET /api/places/{placeId}/visit-info`).
    - TTL: 기본 60초, 트래픽 급증 시 30초로 단축할 수 있도록 설정 값을 외부화(`application.yml` → `statistics.cache.ttl-seconds`).
    - 캐시 값 구조: JSON 직렬화된 DTO(카운트, timestamp, 데이터 출처 enum `CACHE_HIT`/`DB_FALLBACK`).
    - 캐시 미스 시: DB 조회 → 성공하면 캐시에 저장, 실패하면 최근 유효 캐시 반환(`Cache aside` 패턴 유지).
    - 캐시 무효화: 배치가 완료되면 동일 키 삭제 → 자정 집계 이후 첫 조회가 최신 데이터로 채워지도록 함.
  - [ ] DB 조회 실패 시 대체 흐름(기존 캐시 반환) 결정
- [x] 조회/집계 쿼리 초안 (9월 26일 최종)
  ```sql
  -- 실시간 출발/도착/체류 카운트
  SELECT
      SUM(CASE WHEN v.state = 'ACTIVE' THEN 1 ELSE 0 END)            AS active_departures,
      SUM(CASE WHEN v.state = 'ARRIVED' THEN 1 ELSE 0 END)           AS arrived_total,
      SUM(CASE WHEN v.state = 'ARRIVED' AND v.arrived_at::date = CURRENT_DATE THEN 1 ELSE 0 END) AS arrived_today,
      SUM(
          CASE
              WHEN v.state = 'ARRIVED'
                   AND v.deleted_at IS NULL
                   AND (v.expired_at IS NULL OR v.expired_at > now())
                   AND (v.cancelled_at IS NULL OR v.cancelled_at > now())
              THEN 1 ELSE 0
          END
      ) AS staying_now
  FROM visits v
  WHERE v.place_id = :placeId
    AND v.state IN ('ACTIVE', 'ARRIVED');

  -- 캐시 미스 시 최근 N분(기본 5분) 스냅샷 조회를 위해 created_at/updated_at 인덱스 활용
  ```
- [ ] 데이터/인프라 점검
  - [ ] `visits` 인덱스 및 상태 값 확인
  - [ ] Redis 사용 가능 환경 구성(local/test)
  - [ ] Redis 의존성/설정 샘플 공유 (`build.gradle`, `application.yml`) (9월 26일 최종)
  - [ ] Docker 기반 Redis 기동 확인(`redis:7-alpine`, 포트/볼륨) 및 개발자 가이드 정리
- [ ] 서비스/컨트롤러 구현
  - [ ] 통계 조회 Service (캐시 조회 → 미스 시 DB → 캐시 저장)
  - [ ] `GET /api/places/{placeId}/stats` Controller 및 유효성 검사
- [ ] DTO/응답 설계
  - [ ] `PlaceStatsResponseDTO`
  - [ ] 오류 응답 규격 점검
- [ ] 테스트
  - [ ] 단위 테스트: 캐시 히트/미스/예외 흐름
  - [ ] 통합 테스트: 임시 데이터로 카운트 검증, Redis Stub 활용
- [ ] 문서화
  - [ ] API 스펙 및 예시 응답 정리
  - [ ] Task 리스트 업데이트
  - [ ] Redis 운영/캐시 무효화 절차 문서 반영

## UC-Stat-02: 실시간 방문 현황 및 예측
- [ ] 요구 정밀화
  - [ ] 예상 대기 인원 계산 방식 정의
  - [ ] 시간대별 패턴 계산 범위(과거 2주 동일 요일 등) 확정
  - [ ] 외부 예측 모델/날씨 API 연동 범위 및 I/F 결정
- [x] 조회/집계 쿼리 초안 (9월 26일 최종)
  ```sql
  -- 실시간 현황
  SELECT
      SUM(CASE WHEN v.state = 'ARRIVED' THEN 1 ELSE 0 END) AS current_arrivals,
      SUM(CASE WHEN v.state = 'ACTIVE' THEN 1 ELSE 0 END)  AS in_transit,
      SUM(
          CASE
              WHEN v.state = 'ARRIVED'
                   AND v.deleted_at IS NULL
                   AND (v.expired_at IS NULL OR v.expired_at > now())
                   AND (v.cancelled_at IS NULL OR v.cancelled_at > now())
              THEN 1 ELSE 0
          END
      ) AS staying_now
  FROM visits v
  WHERE v.place_id = :placeId
    AND v.state IN ('ACTIVE', 'ARRIVED');

  -- 패턴 데이터: 과거 14일 동일 요일
  SELECT
      date_kst,
      hourly_arrives,
      hourly_starts
  FROM place_daily_stats
  WHERE place_id = :placeId
    AND date_kst >= CURRENT_DATE - INTERVAL '14 days'
    AND EXTRACT(DOW FROM date_kst) = EXTRACT(DOW FROM CURRENT_DATE)
  ORDER BY date_kst DESC;
  ```
- [ ] 데이터 조회/계산 로직
  - [ ] 실시간 현황 쿼리 (`visits` 상태별 카운트)
  - [ ] `place_daily_stats` 기반 패턴 계산 컴포넌트
  - [ ] Alternate Flow(데이터 부족 시 기본값) 처리
- [ ] 예측/추천 연계
  - [ ] 예측 서비스 Stub 또는 실제 호출 구현
  - [ ] 날씨 API 연동/Mock 구성
- [ ] 서비스/컨트롤러 구현
  - [ ] `GET /api/places/{placeId}/visit-info` Controller
  - [ ] Service: 실시간 현황 + 패턴 + 예측 통합
- [ ] 테스트
  - [ ] 단위 테스트: 현황/예측 계산 시나리오
  - [ ] 통합 테스트: Repository Stub, 외부 API Mock
  - [ ] 캐시 통합 테스트/통신 장애 fallback 시나리오
- [ ] 문서화
  - [ ] 응답 예시 및 예측 항목 설명
  - [ ] Task 업데이트

## UC-Batch-01: 일일 자정 리셋 배치
- [ ] 배치 설계
  - [x] Cron 스케줄(00:00 KST) 설정/재시도 전략 (9월 26일 최종)
    - 스케줄: `0 0 * * * Asia/Seoul` (`@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")`).
    - 실행 구조: 배치 진입점 → `@Transactional` 서비스 호출로 UPSERT 처리, 통계 계산은 단일 트랜잭션 안에서 수행.
    - 재시도: 3회까지 고정 backoff(10초)로 재시도, 모두 실패 시 마지막 예외 로그 + 슬랙 알림 트리거.
    - 중복 실행 방지: 배치 시작 시 `distributed lock (Redis SETNX, 2분 TTL)` 확보 후 진행, 종료 시 해제.
  - [x] 실패 알람/로그 정책 (9월 26일 최종)
    - 표준 로거에 `INFO`(시작/종료), `WARN`(재시도), `ERROR`(최종 실패) 기록.
    - 최종 실패 시 `BatchFailureEvent` 발행 → Slack/Webhook 리스너가 알람 발송.
    - 성공 시 `place_daily_stats.last_aggregated_at` 갱신으로 최종 실행 시각을 API(`GET /api/batch/status/last`)에서 조회 가능.
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
  INSERT INTO place_daily_stats (date_kst, place_id, starts, arrives, reviews, likes, hourly_arrives, hourly_starts, peak_hour)
  VALUES (...)
  ON CONFLICT (date_kst, place_id)
  DO UPDATE SET
      starts = EXCLUDED.starts,
      arrives = EXCLUDED.arrives,
      reviews = EXCLUDED.reviews,
      likes = EXCLUDED.likes,
      hourly_arrives = EXCLUDED.hourly_arrives,
      hourly_starts = EXCLUDED.hourly_starts,
      peak_hour = EXCLUDED.peak_hour,
      updated_at = now(),
      last_aggregated_at = now();
  ```
- [ ] 데이터 집계 로직
  - [ ] `visits`/`reviews`/`daily_likes`를 이용한 전일 통계 계산 쿼리
  - [ ] `place_daily_stats` UPSERT 구현
- [ ] 배치 API/상태 조회
  - [ ] `POST /api/batch/midnight-reset` 내부 엔드포인트
  - [ ] `GET /api/batch/status/last` 최근 실행 결과 조회
- [ ] 시간대 통계/예측 재학습
  - [ ] 시간대별 통계 계산
  - [ ] 예측 모델 재학습 트리거 정의
- [ ] 테스트
  - [ ] 단위 테스트: 집계 로직
  - [ ] 통합 테스트: 배치 실행 후 통계 업데이트 확인
  - [ ] Scheduler 동작 검증(Mock 또는 실제 Trigger)
- [ ] 문서/운영
  - [ ] 배치 운영 가이드 및 재실행 절차 문서화
  - [ ] 모니터링 지표 정의

## 공통 마무리
- [ ] 전체 `./gradlew test` 통과 확인
- [ ] README/summary 문서 업데이트
- [ ] Task 진행 상황을 `tasks/review-like-summary.md` 등 문서에 반영
