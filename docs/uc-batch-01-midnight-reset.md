# UC-Batch-01 일일 자정 리셋 배치 (9월 26일 최종)

## 개요
- **역할**: 전일 방문/리뷰/좋아요 데이터를 집계하여 `place_daily_stats`에 저장하고 캐시를 무효화.
- **스케줄**: `@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")` — 매일 00:00 KST 실행.
- **수동 호출**: `POST /api/batch/midnight-reset` (옵션으로 `date` 지정 가능).
- **상태 확인**: `GET /api/batch/status/last`.

## 집계 항목
| 지표 | 설명 |
| --- | --- |
| `starts` | 전일 `started_at`(KST) 기준 방문 수 |
| `arrives` | 전일 `arrived_at`(KST) 기준 도착 수 |
| `reviews` | 전일 작성된 `ACTIVE` 리뷰 수 |
| `likes` | 전일 `daily_likes` 테이블의 `ACTIVE` 건수 |
| `hourly_arrives` | 전일 시간대별 도착 수(JSON) |
| `hourly_starts` | 전일 시간대별 출발 수(JSON) |
| `peak_hour` | 도착 수가 가장 많은 시간대(없으면 `null`) |

## 처리 순서
1. `PlaceDailyStatsBatchRepository`가 `visits`, `reviews`, `daily_likes` 데이터를 KST 기준으로 집계.
2. `DailyStatsBatchService`가 시간대별 지도와 총계 데이터를 생성해 `place_daily_stats`에 `INSERT ... ON CONFLICT`로 업서트.
3. 업서트 후 해당 `placeId`의 캐시를 모두 삭제 (`PlaceStatsCacheService`, `PlaceVisitInfoCacheService`).
4. 예측 재학습 훅(`DailyStatsPredictionService.scheduleRetraining`)을 호출해 모델 업데이트 트리거.
5. 배치 실행 결과(`targetDate`, `executedAt`, `processedPlaces`)를 상태로 보관.

## API 응답 예시
### POST `/api/batch/midnight-reset`
```json
{
  "targetDate": "2024-09-25",
  "executedAt": "2024-09-26T00:00:02Z",
  "processedPlaces": 128
}
```

### GET `/api/batch/status/last`
```json
{
  "running": false,
  "success": true,
  "targetDate": "2024-09-25",
  "executedAt": "2024-09-26T00:00:02Z",
  "message": "COMPLETED"
}
```

## 향후 개선
- Redis 분산 락(SETNX) 적용으로 중복 실행 방지 강화.
- 배치 결과를 별도 테이블에 기록하여 다중 인스턴스 환경에서 공유.
- 예외 발생 시 Slack/Webhook 알림 연동.
