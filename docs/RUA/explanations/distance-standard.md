# 거리 계산 표준화 가이드

태스크 **1.3 거리 계산 표준화**를 진행하며 정리한 내용을 문서화했습니다. PostGIS에서 거리/반경 계산을 할 때 항상 **미터 단위**를 얻고, 이후 개발자들이 동일한 방식으로 구현할 수 있도록 기준을 제공합니다.

---

## 1. 왜 표준화가 필요한가?

- 현재 `places.location` 컬럼은 `geometry(Point,4326)` 타입입니다. `ST_DWithin(location, point, 30)`처럼 geometry 타입을 그대로 사용하면 **단위가 도(°)** 이기 때문에 30이라는 숫자가 30m가 아닌 30도(약 3,000km)에 해당합니다.
- PostGIS의 `geography` 타입 또는 `::geography` 캐스팅을 사용해야 거리를 **미터** 단위로 계산할 수 있습니다.
- 정확한 거리 계산은 지오펜스 판정, 반경 검색, 룰렛 후보군 생성 등 모든 기능의 근간이 되므로, 사전에 통일된 템플릿을 정의해야 합니다.

> 참고: 1.1 태스크에서 `location` 컬럼을 `geography(Point,4326)`로 변환할 예정입니다. 그 전까지는 아래와 같이 `::geography` 캐스팅을 사용합니다.

---

## 2. 표준 SQL 템플릿

### 2.1 반경 필터 (`ST_DWithin`)

```sql
WITH user_point AS (
    SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS point
)
SELECT p.place_id, p.name, u.point
FROM places p
CROSS JOIN user_point u
WHERE ST_DWithin(p.location::geography, u.point, :radius_meters);
```

### 2.2 거리 계산 (`ST_Distance`)

```sql
WITH user_point AS (
    SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS point
)
SELECT p.place_id,
       ST_Distance(p.location::geography, u.point) AS distance_m
FROM places p
CROSS JOIN user_point u
ORDER BY distance_m ASC
LIMIT :size;
```

**핵심 포인트**
- `ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)` 로 사용자의 위치를 geometry로 만든 뒤, `::geography`로 변환합니다.
- `places.location::geography` 로 컬럼도 geography로 캐스팅합니다.
- 이제 `:radius_meters`, `distance_m` 값이 **미터 단위**가 됩니다.

---

## 3. Repository / Querydsl 예시

### 3.1 Native Query 예시

```java
@Query(value = """
WITH user_point AS (
    SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS point
)
SELECT p.place_id,
       ST_Distance(p.location::geography, u.point) AS distance_m
FROM places p
CROSS JOIN user_point u
WHERE ST_DWithin(p.location::geography, u.point, :radius)
ORDER BY distance_m ASC, p.place_id ASC
LIMIT :size
""", nativeQuery = true)
List<Object[]> search(@Param("lat") double lat,
                      @Param("lng") double lng,
                      @Param("radius") double radiusMeters,
                      @Param("size") int size);
```

### 3.2 Querydsl (예시 코드 스니펫)

```java
QPlace place = QPlace.place;
Expression<Geometry> userPoint = Expressions.goe(
    "ST_SetSRID(ST_MakePoint({0}, {1}), 4326)::geography",
    Expressions.constant(lng),
    Expressions.constant(lat)
);

BooleanExpression within = Expressions.booleanTemplate(
    "ST_DWithin({0}::geography, {1}, {2})",
    place.location, userPoint, Expressions.constant(radiusMeters)
);

NumberExpression<Double> distance = Expressions.numberTemplate(
    Double.class,
    "ST_Distance({0}::geography, {1})",
    place.location, userPoint
);

queryFactory.select(place, distance)
    .from(place)
    .where(within)
    .orderBy(distance.asc(), place.placeId.asc())
    .limit(size)
    .fetch();
```

> 위 코드는 개념 예시입니다. 실제 구현에서는 Querydsl custom SQL template 지원을 적절히 사용하세요.

---

## 4. 검증 방법

1. **샘플 쿼리 실행**
   ```sql
   SELECT ST_Distance(
       (SELECT location FROM places WHERE provider_id = '3030000-101-2025-00023')::geography,
       ST_SetSRID(ST_MakePoint(127.0404, 37.5470), 4326)::geography
   );
   ```
   → 출력이 0~수 m 수준이면 성공 (같은 위치).

2. **반경 필터 검증**
   ```sql
   WITH up AS (
       SELECT ST_SetSRID(ST_MakePoint(127.0404, 37.5470), 4326)::geography AS pt
   )
   SELECT COUNT(*)
   FROM places p
   CROSS JOIN up
   WHERE ST_DWithin(p.location::geography, up.pt, 30);
   ```
   → 30m 이내 장소 수가 나옵니다. radius 값을 바꿨을 때 기대대로 증가/감소하는지 확인합니다.

3. **실제 거리 검증**
   - 주소가 명확한 두 지점을 골라 Google Maps 등에서 실제 거리를 측정.
   - 동일 좌표로 `ST_Distance` 결과를 비교해 오차 범위(수 m 이내)인지 확인합니다.

4. **실행 계획 확인**
   ```sql
   EXPLAIN ANALYZE
   SELECT place_id
   FROM places
   WHERE ST_DWithin(location::geography, ST_SetSRID(ST_MakePoint(127.0,37.5),4326)::geography, 30);
   ```
   - GiST 인덱스가 사용되지 않으면 1.1(geography 전환) 태스크 이후 다시 확인합니다.

---

## 5. 자주 묻는 질문

### Q1. 왜 `ST_SetSRID`가 필요하나요?
> PostGIS는 좌표 참조 시스템(SRID)을 알아야 거리 계산을 할 수 있습니다. 입력 좌표(경도/위도)는 WGS84(4326)이므로 반드시 SRID를 지정해야 합니다.

### Q2. 왜 CROSS JOIN user_point를 쓰나요?
> 동일한 사용자 좌표를 여러 번 계산하지 않고, 한 번 정의해서 재사용하기 위함입니다. 또한 가독성이 좋아집니다.

### Q3. geometry 컬럼에 GiST 인덱스를 두었는데, geography 캐스팅을 하면 인덱스를 못 쓰나요?
> 경우에 따라 인덱스 활용이 어렵습니다. 그래서 1.1 태스크에서 컬럼 타입을 `geography(Point,4326)`로 변환할 예정입니다. 그 전까지는 캐스팅을 사용하며, 성능 저하가 걱정된다면 테스트를 통해 확인하세요.

### Q4. radius를 km로 쓰고 싶으면?
> 미터 단위로 계산하므로 `:radius_meters`에 `km * 1000` 값을 넣으면 됩니다.

---

앞으로 모든 거리/반경 관련 로직은 이 템플릿을 기반으로 구현하세요. 구현 후에는 꼭 검증 쿼리를 실행해 실제 장비에서 미터 단위로 계산되는지 확인합니다.
