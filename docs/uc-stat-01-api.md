# UC-Stat-01 음식점 종합 통계 조회 API (9월 30일 최종)

## 엔드포인트 개요
- **HTTP Method**: `GET`
- **URL**: `/api/places/{placeId}/stats`
- **경로 변수**
  - `placeId` (`Long`, required): 통계를 확인할 장소 ID. 양수만 허용.
- **인증**: JWT 기반 사용자 인증 (기존 Feed 서비스와 동일 흐름).

## 동작 흐름
1. Redis 캐시(`place:stats:{placeId}`)에서 직렬화된 통계 응답을 조회.
2. 캐시가 없거나 역직렬화에 실패하면 DB에서 스냅샷을 재계산.
   - `visits` 테이블에서 누적 도착(`arrived_at IS NOT NULL`) 인원과 특정 시간대(11~12시, 12~13시) 최근 14일 평균 도착 인원을 구함.
   - `daily_likes` 테이블에서 `status='ACTIVE'`인 좋아요 누적 수를 함께 반환.
   - 장소 존재 여부는 `PlaceReadRepository#findNameById`로 확인하며, 사용자에게는 ID 대신 이름을 제공.
3. DB 조회 중 장애가 발생할 경우 기존 캐시 값이 존재하면 해당 값을 즉시 반환하고, 장애는 WARN 로그로 기록.
4. 계산 결과를 Redis에 TTL(기본 300초)로 저장 후 응답.

## 응답 필드 정의
| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `placeName` | `String` | 통계를 조회한 장소 이름 |
| `totalVisitors` | `long` | 누적 방문자 수 (`visits.arrived_at IS NOT NULL`) |
| `totalLikes` | `long` | 누적 좋아요 수 (`daily_likes.status = 'ACTIVE'`) |
| `arrivals11To12` | `long` | 최근 14일(당일 포함) 11시~12시 도착 인원 일평균 |
| `arrivals12To13` | `long` | 최근 14일(당일 포함) 12시~13시 도착 인원 일평균 |

> 내부 모니터링을 위해 `generatedAt`, `cacheTtlSeconds`, `dataSource` 값을 유지하지만 API 응답에는 포함하지 않습니다.

## 응답 예시
```json
{
  "placeName": "홍대맛집",
  "totalVisitors": 120,
  "totalLikes": 45,
  "arrivals11To12": 8,
  "arrivals12To13": 5
}
```

## 캐시 전략
- **키 패턴**: `place:stats:{placeId}`
- **TTL**: 기본 300초(5분) (`statistics.cache.ttl-seconds`로 조정 가능)
- **무효화**: 자정 배치 완료 시 또는 수동 갱신 시 `DEL place:stats:{placeId}` 실행.
- **장애 대응**: Redis 사용 실패 시 로그만 남기고 DB 계산 결과를 즉시 반환.

## 통합 테스트 참고
- Postgres 기반 통합 테스트는 운영 DB 환경 확보 시 추가 예정. 현재는 단위 테스트로 로직을 검증.

## 향후 과제
- Redis Stub/Embedded Redis 기반 통합 테스트 보강.
- 오류 응답 규격 정리 및 문서화.
- 배치(`UC-Batch-01`) 완료 시 자동 캐시 무효화 트리거 구현.
