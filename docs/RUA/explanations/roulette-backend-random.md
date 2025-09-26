# 3.2~3.5 룰렛 API(백엔드 무작위 선택) 구현 가이드

`/api/v1/recommendations/roulette` 엔드포인트에서 거리/카테고리 조건을 받아 **백엔드에서 직접 후보를 조회하고 난수로 추천**하는 절차를 정리했습니다.

## 1. 요구사항 요약

1. **입력**: `lat`, `lng`, `radius`, `categories[]`(옵션), `limit`(옵션), `seed`(옵션).
2. **후보 조회**: PostGIS `ST_DWithin` + 카테고리 필터로 후보 리스트 가져오기.
3. **무작위 선택**: 서버에서 균등 확률로 하나를 추출해 응답.
4. **멱등 재생**: 동일 `Idempotency-Key` + 동일 파라미터로 60초 이내 재호출 시 같은 응답 재생.
5. **seed 지원**: 지정 시 항상 같은 후보가 나오도록 난수 시드 제어(기본은 비결정적).

## 2. DTO & 요청/응답 예시

```json
POST /api/v1/recommendations/roulette
Headers: Idempotency-Key: 123e4567-e89b-12d3-a456-426614174000
{
  "lat": 37.5665,
  "lng": 126.9780,
  "radius": 500,
  "categories": ["korean", "noodles"],
  "limit": 200,
  "seed": null
}
```

응답:
```json
{
  "success": true,
  "data": {
    "placeId": 123,
    "name": "순대국명가",
    "distanceMeters": 82.4,
    "categories": ["korean"],
    "meta": {
      "candidateCount": 52,
      "replayed": false
    }
  },
  "timestamp": "2025-02-27T05:00:00Z"
}
```

## 3. 서비스 구현 흐름

1. 멱등키 검증(3.1) → `RouletteService.recommend(request, key)` 호출
2. 서비스는 멱등 저장소(`IdempotencyStore`, 3.3 단계에서 구현)와 연동해 다음을 수행
   ```java
   return idempotencyStore.replayOrRun(idempotencyKey, request, () -> {
       List<Candidate> candidates = placeRepository.findRouletteCandidates(...);
       if (candidates.isEmpty()) throw new RecommendationException(ErrorCode.ROULETTE_NO_CANDIDATE);
       int index = selectIndex(candidates.size(), request.getSeed());
       Candidate chosen = candidates.get(index);
       return RouletteResponseFactory.from(chosen, candidates.size());
   });
   ```
3. `selectIndex`는 `ThreadLocalRandom` 또는 `new Random(seed)`로 균등 난수를 계산.
4. 결과를 `RouletteResponse`로 감싸고, 후보 수/재생 여부 등의 메타 데이터를 함께 반환.

## 4. Repository SQL 예시

```sql
SELECT p.place_id,
       p.name,
       p.category,
       ST_Distance(p.location, up.point) AS distance_m
FROM places p
CROSS JOIN (
    SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS point
) up
WHERE ST_DWithin(p.location, up.point, :radius)
  AND (:categories IS NULL OR p.category && :categories)
ORDER BY p.place_id
LIMIT :limit;
```
- `:categories`는 배열 파라미터로 전달(`String[]`).
- 필요 시 `ORDER BY RANDOM()` 대신 애플리케이션에서 난수 선택으로 균등 분포 유지.

## 5. 멱등 재생 (3.3)

- Redis 키: `idemp:roulette:{idempotencyKey}`
- 저장 값: `requestHash`, `responseBody`, `headers`, `expiresAt`
- 동일 키 + 동일 `requestHash` → 저장된 응답 재생, 다르면 409 반환.
- TTL 기본 60초.

## 6. seed 옵션 (3.4)

- `seed`가 null 아니면 `new Random(seed)` 사용 → 동일 seed 요청에 항상 같은 후보.
- 비워두면 `ThreadLocalRandom.current()`.

## 7. 테스트/문서화 (3.5)

- **분포 테스트**: 동일 조건 1000회 호출 → 각 후보가 ±5% 이내인지 검증.
- **멱등 테스트**: 동일 멱등키 재호출 시 저장된 응답을 재생하는지 확인.
- **seed 테스트**: 특정 seed로 호출해 반복 결과 동일 확인.
- **후보 없음**: 빈 후보 시 204 또는 `ErrorCode.ROULETTE_NO_CANDIDATE` 응답.
- OpenAPI(`/docs/openapi/openapi-v1.yaml`)에 요청/응답/seed/멱등 시나리오 반영.

## 8. 참고 문서

- `docs/RUA/tasks-prd-v1-점심-추천-mvp.md` 3.2~3.5 항목
- 컨트롤러 개요: `docs/RUA/explanations/roulette-api.md`
- 프런트 협업 가이드: `docs/RUA/explanations/frontend-roulette-random.md` (백엔드 무작위 전환 사항 업데이트 필요)

이 문서를 기준으로 룰렛 API backend random 기능을 구현하고, 이후 멱등/seed/테스트/문서화 작업을 순차적으로 진행하세요.
