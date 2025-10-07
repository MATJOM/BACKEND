# Lunch Recommendation MVP 완료 기능 학습 가이드

> 기준 문서: `docs/RUA/tasks-prd-v1-점심-추천-mvp.md` (2025-09-19)
> 범위: 2.0 검색 API ~ 4.0 세션 라이프사이클 + 공통 인프라(레이트리밋, 멱등, 응답 규약)

## 0. 어떻게 읽을지

1. 각 기능별 *사전 지식 → 핵심 흐름 → 코드 리딩 순서* 순으로 따라가며 학습합니다.
2. 코드 레벨에서는 반드시 **Controller → Service → Repository/Infra** 순으로 추적해 흐름을 재구성합니다.
3. 읽으면서 떠오르는 질문은 `// CHECK:` 주석을 직접 남기거나 노트에 기록했다가 저와 세션에서 확인하세요.

### 추천 사전 지식

- PostGIS 기본 (SRID, `ST_DWithin`, `ST_Distance`) – 2.0, 3.0, 4.0에 모두 필요
- Redis 자료구조 & `StringRedisTemplate` (TTL, `INCR`, `SETNX`) – 2.2 캐시, 3.3 멱등, 4.6 특권 초기화, 2.5 레이트리밋
- Spring Web MVC (Controller, `@Validated`, `@ModelAttribute`, `@RequestBody`)
- Spring Validation (`jakarta.validation`), 예외 처리 (`@RestControllerAdvice`)
- Spring Scheduling & 트랜잭션 경계 (`@Scheduled`, `@Transactional`)
- JWT/보안은 향후 태스크(8.x)에서 진행 예정이라, 지금은 간단히 Security Filter Chain 구조만 이해하면 됩니다.

---

## 1. 2.x 장소 검색 API (`/api/v1/places`)

### 1.1 목표 요약
- 주어진 위/경도, 반경, 커서로 PostGIS에서 장소를 조회하고 거리순 ASC로 페이징합니다.
- Redis(60초) 캐시를 통해 동일 조건 요청을 단시간에 재사용합니다.
- 결과가 500건을 넘으면 `meta.reason="too_many_results"`로 안내합니다.
- GET 요청에는 `RateLimitFilter`로 10회/10초(user+ip) 제한을 건 뒤, 헤더에 잔여량을 내려줍니다.

### 1.2 코드 핵심 흐름
1. `PlaceController.getPlaces()` (`src/main/java/com/matjom/matjom/place/api/PlaceController.java:18`)  
   - `@ModelAttribute` + `@Valid`로 쿼리 스트링 검증 → `PlaceSearchRequest`.
2. `PlaceSearchService.search()` (`src/main/java/com/matjom/matjom/place/service/PlaceSearchService.java:30`)  
   - 파라미터 정규화 (`radiusOrDefault`, `sizeOrDefault`).
   - 커서 파싱 (`PlaceSearchCursor.from`) → `distance`, `lastId` 기준.
   - Redis 캐시 키 구성 → `ops.get` → 캐시 미스면 PostGIS 질의 수행.
   - 500+ 초과는 `meta.reason/meta.suggest` 설정.
   - 응답 직렬화 후 Redis TTL 60초 저장.
3. `PlaceRepository.search()` (`src/main/java/com/matjom/matjom/place/repository/PlaceRepository.java:28`)  
   - `ST_DWithin` + `ST_Distance`를 활용한 Native SQL.
   - 커서 조건 `(distance > :cursorDistance) OR (distance = :cursorDistance AND place_id > :cursorLastId)` 로 중복 방지.
   - TODO: `filters` 파라미터는 아직 미구현(후속 태스크, 2.8 예정).

#### 1.2.1 `PlaceSearchRequest` 필드 해설
- `lat` / `lng` – `@NotNull`과 범위 제한(-90~90, -180~180)으로 입력 유효성을 보장하며 PostGIS 질의의 기준 좌표가 됩니다.
- `radius` – `@Positive`만 검증하고 미지정 시 `radiusOrDefault(…)`가 300m(`DEFAULT_RADIUS_METERS`) 기본값을 사용합니다.
- `size` – 페이지 크기를 요청하며 `sizeOrDefault(…)`가 null이면 20(`DEFAULT_PAGE_SIZE`), 지정되면 서비스 층에서 500 상한을 적용합니다.
- `cursor` – `distance:lastId` 포맷의 커서 문자열. `parseCursor()`가 정규식 검증 후 `PlaceSearchCursor`로 변환해 서비스가 예외 없이 사용할 수 있게 합니다.
- `filters` – 최대 200자 제한만 두고 있는 선택 입력. 현재 Repository에서는 미사용이며 후속 필터링 작업을 위해 예약되었습니다.

> 보조 메서드 정리
> - `radiusOrDefault(double defaultValue)` – 반경 미지정 시 기본값을 적용합니다.
> - `sizeOrDefault(int defaultValue)` – 페이지 크기 미지정 시 기본 20을 적용합니다.
> - `parseCursor()` – DTO가 커서 파싱 책임을 가져 서비스 로직의 검증 부담을 낮춥니다.

#### 1.2.2 `PlaceSearchService` 상수 용도
- `DEFAULT_RADIUS_METERS` – 반경 입력이 없을 때 300m 기본값으로 “근처 탐색” 경험을 유지합니다.
- `DEFAULT_PAGE_SIZE` – 페이지 크기 기본값을 20으로 고정해 캐시/커서 기준점을 안정화합니다.
- `MAX_RESULTS_PER_SEARCH` – 500건 상한. `pageSize` 계산과 초과 시 `meta.reason="too_many_results"` 안내에 사용됩니다.
- `MAX_FETCH_LIMIT` – `MAX_RESULTS_PER_SEARCH + 1`을 담는 상수로, 501건 조회로 상한 초과 여부를 감지하려는 의도입니다. 현재 구현은 직접 `+ 1`을 사용하고 있어 미사용 상태입니다.
- `CACHE_TTL` – Redis 캐시 TTL 60초. `writeCache`에서 `ops.set(…, CACHE_TTL)`로 적용됩니다.
- `LOW_RESULTS_SUGGEST_THRESHOLD` – 결과가 20건 미만일 때 `meta.reason="low_results"` 안내를 내려 반경 확대를 유도합니다.

#### 1.2.3 `search()` 메서드 라인별 리뷰
```java
public PlaceSearchResponse search(PlaceSearchRequest request) {
    double lat = request.getLat(); // REVIEW: 컨트롤러 검증을 통과한 위도 → PostGIS 쿼리 기준점
    double lng = request.getLng(); // REVIEW: 경도 역시 그대로 거리 계산에 활용
    double radius = request.radiusOrDefault(DEFAULT_RADIUS_METERS); // REVIEW: 입력이 없으면 300m 기본값 적용
    int requestedSize = request.sizeOrDefault(DEFAULT_PAGE_SIZE); // REVIEW: 클라이언트 요청 페이지 크기(없으면 20)
    int pageSize = Math.min(requestedSize, MAX_RESULTS_PER_SEARCH); // REVIEW: 500개 상한으로 서버 부담을 제어
    PlaceSearchCursor cursorToken = request.parseCursor().orElse(null); // REVIEW: 커서 문자열을 DTO가 파싱해 Optional로 전달

    String cacheKey = buildCacheKey(lat, lng, radius, requestedSize, request.getCursor(), request.getFilters()); // REVIEW: 요청 파라미터 전체를 포함한 캐시 키 → 결과 정합성 유지
    ValueOperations<String, String> ops = redisTemplate.opsForValue(); // REVIEW: Redis 문자열 연산 핸들 (get/set)

    PlaceSearchResponse cached = readCache(ops, cacheKey); // REVIEW: 캐시에서 직전 응답을 조회, 손상 시 삭제
    if (cached != null) {
        return cached; // REVIEW: 캐시 히트면 DB 접근 없이 즉시 반환
    }

    int fetchLimit = pageSize == MAX_RESULTS_PER_SEARCH
            ? MAX_RESULTS_PER_SEARCH + 1 // REVIEW: 500 요청 시 501번째까지 조회해 상한 초과 여부 확인
            : pageSize + 1;             // REVIEW: 일반 페이지도 +1로 조회해 다음 커서 존재 여부 판단
    List<PlaceSummary> fetchedSummaries = placeRepository.search(lat, lng, radius, fetchLimit, cursorToken, request.getFilters()); // REVIEW: PostGIS Native SQL 실행 (filters는 후속 과제)

    boolean exceedsMaxResults = fetchedSummaries.size() > MAX_RESULTS_PER_SEARCH; // REVIEW: 501건 이상이면 too_many_results 메타 생성
    String nextCursor = buildNextCursor(fetchedSummaries, pageSize); // REVIEW: pageSize+1 결과에서 다음 커서 생성

    List<PlaceSummary> pageSummaries = trimToPage(fetchedSummaries, pageSize); // REVIEW: 실제 응답 크기로 리스트 슬라이스
    PlaceSearchResponse.Meta meta;
    if (exceedsMaxResults) {
        meta = new PlaceSearchResponse.Meta("too_many_results", "검색 반경을 줄이거나 필터를 추가해 주세요."); // REVIEW: 상한 초과 안내 문구
    } else if (pageSummaries.size() < LOW_RESULTS_SUGGEST_THRESHOLD) {
        meta = new PlaceSearchResponse.Meta("low_results", "검색 결과가 적습니다. 반경을 늘리거나 필터를 완화해 보세요."); // REVIEW: 결과가 적을 때 UX 가이드 제공
    } else {
        meta = null; // REVIEW: 특별 안내가 필요 없는 경우 메타 생략
    }
    PlaceSearchResponse response = new PlaceSearchResponse(pageSummaries, nextCursor, meta); // REVIEW: 본문/커서/메타를 묶어 응답 객체 생성

    writeCache(ops, cacheKey, response); // REVIEW: 동일 조건 재요청 대비 60초 캐싱
    return response; // REVIEW: 최종 응답 반환
}
```

#### 1.2.4 `PlaceSummary` 응답 필드
- `placeId` / `name` – 기존과 동일하게 장소 식별자와 이름을 내려줍니다.
- `distanceMeters` – 검색 기준점(요청 lat/lng)으로부터의 거리.
- `latitude` / `longitude` – **신규**. 프론트엔드가 Kakao SDK 지도에 핀을 표시할 때 활용하도록 decimal degrees로 노출합니다. 캐시/커서 로직에는 영향을 주지 않으며, PostGIS 질의에서 바로 가져온 좌표를 그대로 전달합니다.

#### 1.2.5 `PlaceRepository` Native SQL 해설
```sql
WITH user_point AS (
    SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS point
), ranked AS (
    SELECT p.place_id,
           p.name,
           ST_Distance(p.location, up.point, true) AS distance_m,
           p.lat,
           p.lng
    FROM places p
    CROSS JOIN user_point up
    WHERE ST_DWithin(p.location, up.point, :radius, true)
)
SELECT place_id,
       name,
       distance_m,
       lat,
       lng
FROM ranked
WHERE (:cursorDistance IS NULL
    OR distance_m > :cursorDistance
    OR (distance_m = :cursorDistance AND place_id > :cursorLastId))
ORDER BY distance_m ASC, place_id ASC
LIMIT :limit
```

- **user_point CTE**: 요청 lat/lng를 geography 포인트로 변환해 이후 거리 계산을 미터 단위로 맞춥니다. SRID 4326(WGS84) + `::geography` 캐스팅이 핵심입니다.
- **ranked CTE**: `ST_DWithin`으로 반경(:radius, 미터) 내 장소만 필터링하고, `ST_Distance(..., true)`로 great-circle distance를 계산합니다. 여기서 lat/lng 컬럼을 함께 읽어 프론트에 전달할 좌표를 확보합니다.
- **커서 조건**: `distance_m`가 커서 거리보다 크거나(다음 페이지) 동일 거리일 땐 `place_id`가 더 큰 행만 선택해 중복/누락 없는 ASC 정렬을 보장합니다.
- **정렬/제한**: 거리 ASC → ID ASC 정렬 후 `:limit`(pageSize + 1)만큼만 가져와 `nextCursor` 계산에 쓰입니다.
- **RowMapper**: `PlaceSummary` 생성 시 `distance_m`, `lat`, `lng`를 그대로 매핑해 서비스 레이어가 별도 변환 없이 응답을 만들 수 있게 합니다.

### 1.3 사전 지식 & 학습 포인트
- **PostGIS Geography**: `places.location`이 `GEOGRAPHY(Point,4326)`이므로 거리 단위가 미터임을 기억하세요. (`sql/migration/001_places_location_to_geography.sql`)
- **커서 설계**: `distanceMeters:lastId` 포맷은 정렬 기준과 동일해야 정합성을 보장합니다. 정렬 순서를 바꾸면 커서 포맷도 함께 바꿔야 합니다.
- **Redis StringKey TTL**: 캐시 저장 시 `ops.set(key, value, Duration)` 패턴을 익혀두면 다른 멱등/레이트리밋에서도 재사용 가능합니다.
- **레이트리밋 헤더**: 필터가 `X-RateLimit-Remaining`, `Retry-After`를 세팅하므로 클라이언트 가이드에도 동일하게 명시합니다.

### 1.4 리딩 순서 & 실습 체크리스트
1. DTO 검증 규칙 확인 → `PlaceSearchRequest` (`src/main/java/com/matjom/matjom/place/dto/PlaceSearchRequest.java`)
2. 커서 토큰 로직 → `PlaceSearchCursor` (`src/main/java/com/matjom/matjom/place/dto/PlaceSearchCursor.java`)
3. 서비스 캐시 플로우 → `PlaceSearchService.search` (특히 `readCache`/`writeCache`)
4. Repository SQL을 직접 `psql`에 붙여 실행해보며 Explain Plan 확인.
5. 레이트리밋 체인 확인 → `RateLimitFilter.shouldNotFilter` 조건 → `SecurityConfig`에서 어디 위치하는지.
6. 테스트 복기: `PlaceSearchServiceTest` (커서 경계/메타) – 추후 직접 실행하면서 파라미터 조합 연습.

---
## 2. 3.x 룰렛 추천 API (`/api/v1/recommendations/roulette`)

### 2.1 목표 요약
- 조건에 맞는 후보군(최대 500)을 조회하고 균등 랜덤으로 한 건을 선택.
- 60초 내 동일 `Idempotency-Key` + 동일 요청 파라미터면 캐시된 응답을 재사용.
- `seed` 값이 있으면 재현 가능한 추천을 제공해 디버깅/리뷰 용이성 확보.

### 2.2 코드 핵심 흐름
1. `RouletteController.postRoulette()` (`src/main/java/com/matjom/matjom/recommendation/api/RouletteController.java:21`)  
   - `Idempotency-Key` 헤더 필수 + 200자 제한.
2. `RouletteService.recommend()` (`src/main/java/com/matjom/matjom/recommendation/service/RouletteService.java:33`)  
   - 멱등 키 → Redis 키 (`idemp:roulette:{key}`) 구성.
   - 요청 JSON → SHA-256 해시 (키 충돌 방지).
   - `IdempotencyStore.replayOrRun`에서 재생 여부 확인.
   - 최초 실행이면 `executeRecommendation()` 호출 → 후보 조회 + 랜덤 선택.
   - 재생 시 `meta.replayed=true`로 감지 가능.
3. `PlaceRepository.findRouletteCandidates()` (`src/main/java/com/matjom/matjom/place/repository/PlaceRepository.java:68`)  
   - 카테고리 배열 필터(`p.category && :categories`)를 활용.

#### 2.2.1 `RouletteController` 라인별 리뷰
```java
@PostMapping("/roulette")
public ApiResponse<RouletteResponse> postRoulette(
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody RouletteRequest request) {
    String sanitizedKey = validateIdempotencyKey(idempotencyKey); // REVIEW: 빈 헤더/200자 초과 방지
    RouletteResponse response = rouletteService.recommend(request, sanitizedKey); // REVIEW: 멱등 키와 함께 서비스 호출
    return ApiResponse.ok(response); // REVIEW: 공통 응답 규약 적용
}

private String validateIdempotencyKey(String idempotencyKey) {
    if (!StringUtils.hasText(idempotencyKey)) { // REVIEW: 헤더 누락 → IDEMPOTENCY_KEY_REQUIRED
        throw new RecommendationException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }
    String trimmed = idempotencyKey.trim();
    if (trimmed.length() > IDEMPOTENCY_KEY_MAX_LENGTH) { // REVIEW: 200자 초과 방지
        throw new RecommendationException(ErrorCode.INVALID_REQUEST_PARAM,
                "Idempotency-Key 길이는 200자를 초과할 수 없습니다.");
    }
    return trimmed;
}
```

- 컨트롤러 책임은 헤더 검증과 서비스 호출로 한정했습니다. 예외는 `RecommendationException`을 통해 공통 에러 응답으로 매핑됩니다.
- `@Validated` + `@Valid` 조합으로 요청 본문 검증을 Spring이 수행합니다.

#### 2.2.2 `RouletteService.recommend()` 라인별 리뷰
```java
public RouletteResponse recommend(final RouletteRequest request, String idempotencyKey) {
    Objects.requireNonNull(idempotencyKey, "idempotencyKey"); // REVIEW: 컨트롤러에서 보장하더라도 방어 코드
    String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;     // REVIEW: Redis 저장 키 prefix
    String requestHash = computeRequestHash(request);          // REVIEW: 요청 본문을 SHA-256으로 해싱(충돌 방지)

    IdempotencyResult<RouletteResponse> result = idempotencyStore.replayOrRun(
            redisKey,
            requestHash,
            RouletteResponse.class,
            new IdempotencyCallback<RouletteResponse>() {
                @Override
                public RouletteResponse execute() {
                    return executeRecommendation(request);    // REVIEW: 최초 실행 시 실제 추천 로직 수행
                }
            }
    );

    if (result.isReplayed()) {
        return markReplayed(result.getValue());                 // REVIEW: meta.replayed=true로 재생 응답 표시
    }
    return result.getValue();                                   // REVIEW: 새로운 응답 그대로 반환
}

private RouletteResponse executeRecommendation(RouletteRequest request) {
    double radius = request.radiusOrDefault(DEFAULT_RADIUS_METERS);        // REVIEW: 기본 반경 300m
    int limit = Math.min(request.limitOrDefault(DEFAULT_LIMIT), MAX_LIMIT); // REVIEW: 최대 500건 상한

    List<RouletteCandidate> candidates = placeRepository.findRouletteCandidates(
            request.getLat(),
            request.getLng(),
            radius,
            request.categoriesOrNull(),
            limit);

    if (candidates.isEmpty()) {
        throw new RecommendationException(ErrorCode.ROULETTE_NO_CANDIDATE); // REVIEW: 후보가 없으면 204 반환
    }

    int index = selectIndex(candidates.size(), request.getSeed()); // REVIEW: seed 제공 시 결정적 추천, 없으면 균등 랜덤
    RouletteCandidate chosen = candidates.get(index);

    return new RouletteResponse(
            chosen.placeId(),
            chosen.name(),
            chosen.distanceMeters(),
            chosen.categories(),
            chosen.latitude(),
            chosen.longitude(),
            new RouletteResponse.Meta(candidates.size(), false)
    );
}
```

- `computeRequestHash()`는 ObjectMapper로 요청을 직렬화한 뒤 SHA-256 해시를 구합니다. 동일 키라도 본문이 다르면 `IDEMPOTENCY_KEY_CONFLICT`가 발생하도록 설계한 포인트입니다.
- `markReplayed()`는 meta.replayed 플래그만 true로 바꿔 FE가 재생 응답을 인지할 수 있게 합니다.
- `selectIndex()`는 후보가 1개 이하일 때 0을 바로 반환하고, seed가 있는 경우 `new Random(seed)`를 사용해 재현성을 보장합니다.

#### 2.2.3 `ROULETTE_SQL` 구조
```sql
WITH user_point AS (
    SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS point
)
SELECT p.place_id,
       p.name,
       p.category,
       ST_Distance(p.location, up.point, true) AS distance_m,
       p.lat,
       p.lng
FROM places p
CROSS JOIN user_point up
WHERE ST_DWithin(p.location, up.point, :radius, true)
  AND (:categories IS NULL OR p.category && :categories)
ORDER BY p.place_id
LIMIT :limit
```

- 반경 필터: `ST_DWithin`이 geography 타입을 사용해 미터 단위 반경을 적용합니다.
- 카테고리 필터: `p.category && :categories`는 Postgres 배열 교집합 연산자로, 요청한 카테고리와 겹치는 장소만 반환합니다.
- 정렬/limit: 거리 대신 `place_id` ASC로 정렬해 간단한 후보 리스트를 구성하며, 커서 없이 `limit`만 적용합니다.
- `ROULETTE_CANDIDATE_ROW_MAPPER`가 `category` 배열을 `List<String>`으로 변환하고 lat/lng를 함께 매핑해 서비스/응답 DTO에서 바로 사용합니다.

#### 2.2.4 DTO 주석 리뷰
```java
public class RouletteRequest {
    @NotNull(message = "위도(lat)는 필수입니다.")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double lat; // REVIEW: 사용자 위치 위도 (WGS84)

    @NotNull(message = "경도(lng)는 필수입니다.")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double lng; // REVIEW: 사용자 위치 경도 (WGS84)

    @Positive(message = "반경(radius)은 양수여야 합니다.")
    private Double radius; // REVIEW: 검색 반경(미터). null이면 기본 300m

    @Size(max = 5, message = "categories는 최대 5개까지 허용됩니다.")
    private List<@Size(min = 1, max = 30) String> categories; // REVIEW: 카테고리 교집합 필터

    @Positive(message = "limit은 1 이상이어야 합니다.")
    private Integer limit; // REVIEW: 후보 최대 개수. null → 기본 200, 상한 500

    private Long seed; // REVIEW: 동일 seed로 재현 가능한 추천을 만들기 위한 옵션

    public double radiusOrDefault(double defaultValue) { ... }
    public int limitOrDefault(int defaultValue) { ... }
    public List<String> categoriesOrNull() { ... }
}

public record RouletteResponse(Long placeId,
                               String name,
                               double distanceMeters,
                               List<String> categories,
                               double latitude,
                               double longitude,
                               Meta meta) {

    public record Meta(int candidateCount, boolean replayed) { // REVIEW: 후보 수와 멱등 재생 여부
    }
}

public record RouletteCandidate(Long placeId,
                                String name,
                                double distanceMeters,
                                List<String> categories,
                                double latitude,
                                double longitude) { // REVIEW: Repository → Service 전달 객체
}
```

- `RouletteRequest`는 기본값 헬퍼(`radiusOrDefault`, `limitOrDefault`)로 null 처리 부담을 서비스에서 제거하고, 카테고리 배열은 없으면 `null`을 반환해 SQL에서 `:categories IS NULL` 분기를 타도록 설계되었습니다.
- `seed`는 QA나 리플레이 상황에서 동일 결과를 만들기 위한 도구이며, 미지정 시 서버가 균등 난수로 선택합니다.
- `RouletteResponse.Meta`는 추천 당시 후보 수와 재생 여부만 전달해 FE가 UI 및 로그를 제어할 수 있게 합니다.
- `RouletteCandidate`는 repository 레이어 내부 DTO로, 거리·카테고리·좌표를 포함한 최소 정보를 Service에 넘겨 랜덤 선택만 집중하게 합니다.

### 2.3 사전 지식 & 학습 포인트
- **Idempotency Hash**: 요청 본문이 다르면 같은 키라도 `IDEMPOTENCY_KEY_CONFLICT` 예외 발생. 이 정책을 Product/FE와 합의해 두어야 합니다.
- **Random vs ThreadLocalRandom**: seed가 없을 때 `ThreadLocalRandom`, seed가 있을 때 `java.util.Random(seed)` 사용으로 균등성 + 재현성 확보.
- **오류 코드**: 후보군이 없으면 `ErrorCode.ROULETTE_NO_CANDIDATE`(`204`). 클라이언트 UX 설계 시 이 케이스를 별도 처리해야 합니다.

### 2.4 리딩 순서 & 실습 체크리스트
1. 컨트롤러 헤더 검증 로직 이해.
2. 멱등 스토어 구현 비교: `RedisIdempotencyStore` vs `InMemoryIdempotencyStore` – 로컬 profile에서는 InMemory가 대체됩니다.
3. 랜덤 선택 로직과 `RouletteResponse.Meta` 구조 확인.
4. 테스트: `RouletteServiceTest` – 분포/멱등 검증 케이스를 직접 읽고, 필요한 경우 표본 크기를 조정해 실험.
---

## 3. 4.x 세션 라이프사이클 (Start → 위치 이벤트 → 자동/수동 도착 → 타임아웃)

### 3.1 전체 그림

```
Client → POST /sessions (Idempotency) → VisitSessionService.startSession()
      ↳ Visit 저장 (state=ACTIVE, 30분 만료 예정)
      ↳ 상태 이벤트 기록(VisitStateTransitionRecorder)

Client → POST /sessions/{id}/positions
      ↳ VisitPosition 저장
      ↳ DefaultGeoFenceEvaluator.evaluate()
          • 30m 이내 + dwell 180s + 정확도 ≤30m → 자동 ARRIVED
          • 30m 밖: 10초 이내 재진입이면 dwell 유지, 아니면 리셋
      ↳ 상태 변화 시 이벤트 + 도착 시 레이트리밋 초기화

Client → POST /sessions/{id}/arrivals (Idempotency)
      ↳ 시작 후 10~60분 사이 + 30m 이내여야 함
      ↳ Visit.arriveAt() + 이벤트 기록 + replay 플래그

Scheduler (1분 주기) → VisitTimeoutScheduler.expireTimedOutVisits()
      ↳ started_at + 30분 초과 ACTIVE → EXPIRED + 이벤트 기록
```

### 3.2 세션 시작 (Task 4.1)
- **소스**: `VisitSessionController.startSession()` (`src/main/java/com/matjom/matjom/visit/api/VisitSessionController.java:23`)
- **서비스**: `VisitSessionService.startSession()` (`src/main/java/com/matjom/matjom/visit/service/VisitSessionService.java:49`)
  - 사용자/장소 유효성 체크 (`UserRepository`, `PlaceJpaRepository`).
  - ACTIVE 중복 여부 (`VisitRepository.existsByUser_IdAndState`).
  - Visit 엔티티 생성 → `expiresAt = startedAt + 30분`.
  - 멱등 재생 시 `VisitSessionStartResponse.replayed=true`.
- **사전 지식**: UUID 다루기, 멱등 해시 개념, 트랜잭션(중복 세션 방지).
- **실습**: 테스트 `VisitSessionServiceTest.startSessionCreatesNewVisit()`를 디버깅하며 플로우 확인.

#### 3.2.1 `VisitSessionController` 라인별 리뷰
```java
@RestController
@RequestMapping("/api/v1/sessions")
@Validated
public class VisitSessionController {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 200; // REVIEW: 멱등 키 최대 길이

    private final VisitSessionService visitSessionService;
    private final VisitPositionService visitPositionService;

    public VisitSessionController(VisitSessionService visitSessionService,
                                  VisitPositionService visitPositionService) {
        this.visitSessionService = visitSessionService;
        this.visitPositionService = visitPositionService;
    }

    @PostMapping
    public ApiResponse<VisitSessionStartResponse> startSession(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody VisitSessionStartRequest request) {
        String sanitizedKey = validateIdempotencyKey(idempotencyKey); // REVIEW: 공백/길이 검증
        VisitSessionStartResponse response = visitSessionService.startSession(request, sanitizedKey); // REVIEW: 멱등 처리 포함 서비스 호출
        return ApiResponse.ok(response); // REVIEW: 공통 응답 포맷
    }

    @PostMapping("/{sessionId}/positions")
    public ApiResponse<VisitPositionResponse> recordPosition(
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody VisitPositionRequest request) {
        VisitPositionResponse response = visitPositionService.recordPosition(sessionId, request); // REVIEW: 위치 이벤트 저장 + 자동 도착 판정
        return ApiResponse.ok(response);
    }

    @PostMapping("/{sessionId}/arrivals")
    public ApiResponse<VisitManualArrivalResponse> confirmArrival(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("sessionId") Long sessionId,
            @Valid @RequestBody VisitManualArrivalRequest request) {
        String sanitizedKey = validateIdempotencyKey(idempotencyKey); // REVIEW: 수동 도착도 멱등 키 필수
        VisitManualArrivalResponse response = visitSessionService.confirmManualArrival(sessionId, request, sanitizedKey);
        return ApiResponse.ok(response);
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) { // REVIEW: 헤더 누락 or 공백만이면 예외
            throw new SessionException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > IDEMPOTENCY_KEY_MAX_LENGTH) { // REVIEW: 200자 초과 방지
            throw new SessionException(ErrorCode.INVALID_REQUEST_PARAM, "Idempotency-Key 길이는 200자를 초과할 수 없습니다.");
        }
        return trimmed;
    }
}
```

- `startSession`는 멱등 키 검증 후 `VisitSessionService`로 위임한다. 같은 키로 60초 내 동일 요청이 들어오면 캐시된 응답을 재생한다.
- `recordPosition`은 위치 이벤트를 받아 GeoFence 평가와 상태 전이를 수행한다.
- `confirmArrival`은 수동 도착을 멱등 처리하고, 시간/거리 조건을 충족하지 못하면 서비스에서 예외를 일으킨다.

### 3.3 위치 이벤트 처리 & 자동 도착 (Task 4.2~4.4)
- **컨트롤러**: `recordPosition()` (`VisitSessionController`:38)
- **서비스**: `VisitPositionService.recordPosition()` (`src/main/java/com/matjom/matjom/visit/service/VisitPositionService.java:33`)
  - 세션 ACTIVE 여부 확인, 위치 이벤트 저장.
  - `GeoFenceEvaluator.evaluate()` 호출 → `GeoFenceEvaluationResult` 반환.
  - 정확도 >30m 이면 dwell 카운트를 일시 중지(`accuracyPaused=true`).
  - 30m 이내, dwell ≥180s, 정확도 OK → `Visit.arriveAt()` 자동 도착.
  - 상태 변화 시 `VisitStateTransitionRecorder.record()`로 이벤트 저장 + 레이트리밋 초기화 (arrived).
- **지오펜스 구현**: `DefaultGeoFenceEvaluator` (`src/main/java/com/matjom/matjom/visit/geofence/DefaultGeoFenceEvaluator.java`)
  - 거리 계산 → `GeoDistanceCalculator` (Haversine).
  - `visit.startDwellIfAbsent(recordedAt)` / `resetDwell()`로 dwell 타이머 관리.
  - 10초 유예(`GRACE_SECONDS`)로 잠깐 이탈 시 dwell 유지.
- **실습**: `DefaultGeoFenceEvaluatorTest` 경계 케이스 재현 (29.9m vs 30m, 179s vs 180s 등).

### 3.4 수동 도착 (Task 4.5)
- **컨트롤러**: `confirmArrival()` (`VisitSessionController`:48)
- **서비스**: `VisitSessionService.confirmManualArrival()` (`src/main/java/com/matjom/matjom/visit/service/VisitSessionService.java:84`)
  - 멱등 키 스킴: `idemp:sessions:arrival:{sessionId}:{key}` (세션별).
  - 시간 범위 10~60분, 거리 ≤30m 제한.
  - 성공 시 Visit 상태 ARRIVED, 이벤트 소스 `MANUAL_ARRIVAL`.

### 3.5 타임아웃 스케줄러 & 상태 이벤트 (Task 4.6~4.7)
- **Scheduler**: `VisitTimeoutScheduler.expireTimedOutVisits()` (`src/main/java/com/matjom/matjom/visit/service/VisitTimeoutScheduler.java:31`)
  - 1분마다 ACTIVE + startedAt ≤ now-30분 조회 (`VisitRepository.findTimeoutCandidates`).
  - EXPIRED로 전이 후 이벤트 기록(`VisitStateEventSource.TIMEOUT`).
- **이벤트 기록**: `VisitStateTransitionRecorder` + `VisitEventService` (`src/main/java/com/matjom/matjom/visit/service/VisitStateTransitionRecorder.java`, `VisitEventService.java`)
  - 상태 변화 시 VisitEvent 적재 + `VisitPrivilegeService.resetSearchQuotaForArrival()`.
  - 향후 스트리밍/감사 용도로 확장될 수 있음.

### 3.6 추가 참고
- `Visit` 엔티티 (`src/main/java/com/matjom/matjom/visit/entity/Visit.java`)는 상태, 위치, dwell, 메타를 모두 담고 있음.
- `VisitPosition` 엔티티 (`src/main/java/com/matjom/matjom/visit/entity/VisitPosition.java`)는 개별 수신 위치.
- 예외 코드: `ErrorCode` (`src/main/java/com/matjom/matjom/common/exception/message/ErrorCode.java`)에서 정책을 확인하고, FE 문서와 일치하는지 체크.

### 3.7 실습 체크리스트
- Postman/HTTPie로 `start → positions(여러 번) → arrivals` 시나리오를 수동 테스트.
- Redis에서 레이트리밋 키(`rl:places:user:{uuid}`)가 도착 시 삭제되는지 모니터링.
- 스케줄러 테스트를 위해 Visit.startedAt을 과거로 설정한 뒤 `expireTimedOutVisits()` 수동 호출.

---

## 4. 공통 인프라 & 크로스컷팅

### 4.1 응답 규약 & 예외 처리
- `ApiResponse` (`src/main/java/com/matjom/matjom/common/response/ApiResponse.java`) – `{ success, data, error, timestamp }` 표준.
- `ApiResponseBodyAdvice` – 컨트롤러에서 DTO만 반환해도 자동 래핑.
- `GlobalExceptionHandler` – `DomainException` 계층 + 검증 에러 처리.

### 4.2 멱등 스토어
- 인터페이스: `IdempotencyStore` (`src/main/java/com/matjom/matjom/common/idempotency/IdempotencyStore.java`).
- 구현체: `RedisIdempotencyStore` (실서비스), `InMemoryIdempotencyStore` (로컬/테스트).
- 정책 차이: Redis는 `setIfAbsent`로 분산 락 유사 기능, InMemory는 `synchronized`.
- 60초 TTL 기본 – 향후 `application.yml` 외부화(태스크 13.0) 대상.

### 4.3 레이트리밋 체인
- `RedisSearchRateLimiter.consume()` – `INCR` + `expire` → 초과 시 `RateLimitResult.blocked`.
- `RateLimitFilter` – userId/IP 키 모두 소비, 실패 시 `rollback`으로 토큰 복원.
- `SecurityConfig` – `UsernamePasswordAuthenticationFilter` 이전에 레이트리밋 필터 등록.

### 4.4 설정 & 지원 Bean
- `ClockConfig.systemClock()` – `Asia/Seoul` 기준 Clock (스케줄러/서비스 공통).
- `SchedulingConfig` – 스케줄러 활성화.
- `JpaConfig` – Spring Data Auditing KST 적용.

---

## 5. 현재 공백 & 다음 학습 주제

| 영역 | 상태 | 비고 |
|------|------|------|
| 5.x 피드/좋아요 | 미구현 | PRD 문서상 범위 밖 |
| 6.x 레이트리밋 확장 (`RateLimitConfig` route 분기) | 미구현 | 현재 `/places` 전용만 존재 |
| 7.x 전역 `IdempotencyFilter` | 미구현 | 서비스별 멱등 처리로 대체 중 |
| 8.x JWT/JWKS/KeyRotation | 미구현 | `SecurityConfig`는 permitAll 기본 값 |
| 9.x 관측성(Tracing/Metrics) | 미구현 | Micrometer 구성 예정 |
| 10.x OpenAPI 문서화 | 부분 | `docs/openapi/openapi-v1.yaml` 수동 유지 |

다음 학습 세션까지는 2.x~4.x 범위를 확실히 숙지한 뒤, 6.x~10.x 항목 중 우선 순위를 함께 정해 나가겠습니다.

---

## 6. 스스로 점검하기

1. Redis/DB가 다운된 경우 각각 어떤 예외가 발생하고 어디서 핸들링되는지 추적해 보세요.
2. 커서 토큰을 임의로 조작했을 때(예: 음수 distance) 어떤 에러 코드가 내려가는지 확인해 보세요.
3. 자동 도착 요건(30m, 180s, 10s 유예, 정확도 ≤30m)을 그림으로 정리해 보세요.
4. 멱등 키를 일부러 달리해 재요청하면 어떤 결과가 나오는지 실험해 보세요.

필요한 사전 지식이 더 있다면 자유롭게 요청해 주세요. 다음 대화에서는 위 체크리스트 결과와 함께 이해가 부족한 부분을 짚어보겠습니다.
