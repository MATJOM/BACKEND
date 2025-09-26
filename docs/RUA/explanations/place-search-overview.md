# 2.x 장소 검색 API(v1) 종합 정리

`/api/v1/places` 기능은 2.0~2.7 태스크에 걸쳐 구현되었습니다. 본 문서는 전체 흐름과 연관 문서를 한눈에 볼 수 있도록 정리합니다.

---

## 주요 기능

1. **엔드포인트 & 입력 검증 (2.1)**  
   - `PlaceController.getPlaces()`에서 위도/경도/반경/size/cursor/filters 파라미터를 검증.  
   - DTO: `PlaceSearchRequest` (`@Validated` + Lombok)  
   - 참고: `docs/RUA/explanations/place-controller.md`

2. **캐시 + PostGIS 검색 (2.2)**  
   - `PlaceSearchService`가 60초 Redis 캐시 후 Native PostGIS 질의를 수행.  
   - 거리 기준 정렬(`ST_DWithin`, `ST_Distance`).  
   - 참고: `docs/RUA/explanations/place-search-service.md`

3. **커서 페이징 (2.3)**  
   - `PlaceSearchCursor`로 `distance:lastId` 포맷의 커서 파싱/생성.  
   - size+1 로딩 후 트림 → `nextCursor` 계산.  
   - 참고: `docs/RUA/explanations/cursor-pagination.md`

4. **결과 상한/저결과 안내 (2.4)**  
   - 500건 초과 시 `meta.reason="too_many_results"` + 축소 안내.  
   - 20건 미만이면 `meta.reason="low_results"`로 반경/필터 완화 권장.  
   - 참고: `docs/RUA/explanations/result-cap-500.md`

5. **레이트리밋 (2.5)**  
   - `RateLimitFilter` + Redis 카운터로 10회/10초 (userId → ip fallback).  
   - 429 시 `Retry-After`, `X-RateLimit-*` 헤더 제공.  
   - 참고: `docs/RUA/explanations/rate-limit-places.md`

6. **문서화 (2.6)**  
   - `docs/openapi/openapi-v1.yaml`에 요청/응답 스키마, `low_results`/`too_many_results` 예시, 429 헤더 명세 반영.  
   - 참고: `docs/RUA/explanations/openapi-places-spec.md`

7. **테스트 (2.7)**  
   - `PlaceSearchServiceTest`: 캐시 hit/miss, 커서 경계, 상한, 저결과, 무중복 페이징 검증.  
   - 레이트리밋 테스트: `RedisSearchRateLimiterTest`, `RateLimitFilterTest`.  
   - 참고: `docs/RUA/explanations/place-search-service.md` 섹션 4 등.

---

## 데이터베이스 & 스키마

- `places.location`은 `geography(Point,4326)`로 사용하며, `schema-postgres.sql`과 `Place` 엔터티 모두 동일하게 설정.  
- 초기 인덱스: GiST(location), lower(name), GIN(category) 등.

---

## 연관 OpenAPI/문서

| 항목 | 파일 |
| --- | --- |
| OpenAPI 명세 | `docs/openapi/openapi-v1.yaml` |
| 검색 캐시/서비스 설명 | `docs/RUA/explanations/place-search-service.md` |
| 커서 페이징 가이드 | `docs/RUA/explanations/cursor-pagination.md` |
| 상한/저결과 메타 | `docs/RUA/explanations/result-cap-500.md` |
| 레이트리밋 정책 | `docs/RUA/explanations/rate-limit-places.md` |

---

## 마무리 체크

- [x] 코드와 문서 모두 2.0~2.7 요구 사항을 충족.  
- [x] OpenAPI/테스트/레이트리밋/메타 안내가 최신 상태로 반영.  
- [x] 프런트엔드 룰렛 무작위 기능은 별도 문서(`frontend-roulette-random.md`)에 명시.

본 문서를 최신 상태로 유지하여 향후 유지보수 시 진입 장벽을 낮추세요.
