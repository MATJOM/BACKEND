# Places 테이블 인덱스 점검 안내

이 문서는 태스크 **1.2 인덱스 점검/생성**을 처음 접하는 주니어 개발자를 위해 작성되었습니다. 목표는 “왜 이 인덱스가 필요한지, 어떻게 확인하는지”를 0에서 100까지 설명하는 것입니다.

---

## 1. 인덱스가 왜 필요한가요?

데이터베이스에서 인덱스는 책의 목차/색인과 같은 역할을 합니다. 흔히 사용하는 SELECT 쿼리가 아래와 같다고 가정해 봅니다.

```sql
-- 반경 30m 내의 장소를 찾는 지오쿼리
SELECT *
FROM places
WHERE ST_DWithin(location, :user_point, 30);

-- 이름을 검색(대소문자 무시)할 때
SELECT *
FROM places
WHERE lower(name) LIKE '멘야%';

-- 특정 카테고리(예: 일식) 필터
SELECT *
FROM places
WHERE category @> ARRAY['일식'];
```

위 쿼리가 빠르게 실행되려면 아래 인덱스가 필요합니다.

| 인덱스 | 목적 |
| --- | --- |
| `GIST(location)` | PostGIS 공간 연산(`ST_DWithin`, `ST_Distance`)을 빠르게 하기 위한 공간 인덱스 |
| `btree(lower(name))` | 대소문자 무시 문자열 검색을 위한 함수 인덱스 |
| `GIN(category)` | 배열(여러 카테고리)에서 원하는 값을 찾기 위한 인덱스 |


## 2. 지금 인덱스가 있는지 어떻게 확인하나요?

1. Postgres 컨테이너에 접속해서 `
   \d places
   ` 명령을 실행합니다.

```bash
docker exec -it db-postgis psql -U devuser -d matjom_dev
```

2. `
   \d places
   ` 명령 결과의 `Indexes:` 부분에서 필요한 인덱스가 존재하는지 확인합니다.

예시 출력:

```
Indexes:
    "places_pkey" PRIMARY KEY, btree (place_id)
    "idx_places_category" gin (category)
    "idx_places_eupmyeondong" btree (addr_eupmyeondong)
    "idx_places_location" gist (location)
    "idx_places_lower_name" btree (lower(name::text))
    "idx_places_sido" btree (addr_sido)
    "idx_places_sigungu" btree (addr_sigungu)
    "uq_places_provider_id" UNIQUE CONSTRAINT, btree (provider_id)
```

위와 같이 `idx_places_location`, `idx_places_lower_name`, `idx_places_category`가 있으면 요구 사항을 충족합니다.


## 3. 만약 없다면 어떻게 만들어야 하나요?

### 3.1 공간 인덱스(GiST)

```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_location
    ON places USING gist (location);
```

### 3.2 lower(name) 인덱스

```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_lower_name
    ON places (lower(name));
```

### 3.3 category GIN 인덱스

```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_places_category
    ON places USING gin (category);
```

> `CONCURRENTLY` 키워드를 사용하면 테이블이 잠기는 것을 최소화하면서 인덱스를 만들 수 있습니다. 다만 트랜잭션 블록 안에서는 사용할 수 없으니 주의하세요.


## 4. 인덱스가 정말 쓰이는지 검증하는 방법

`EXPLAIN ANALYZE`로 실행 계획을 확인할 수 있습니다.

1. 공간쿼리 확인:

```sql
EXPLAIN ANALYZE
SELECT place_id
FROM places
WHERE ST_DWithin(location, ST_MakePoint(127.0276, 37.4979)::geography, 30);
```

결과에 `Index Scan using idx_places_location on places` 같은 줄이 나타나면 GiST 인덱스가 제대로 사용되고 있는 것입니다.

2. 이름 검색 확인:

```sql
EXPLAIN ANALYZE
SELECT place_id
FROM places
WHERE lower(name) LIKE '멘야%';
```

`Index Scan using idx_places_lower_name`가 보이면 성공입니다.

3. 카테고리 필터 확인:

```sql
EXPLAIN ANALYZE
SELECT place_id
FROM places
WHERE category @> ARRAY['일식'];
```

`Bitmap Index Scan on idx_places_category`가 출력되면 GIN 인덱스가 사용 중임을 의미합니다.


## 5. 이번 확인 결과 요약

2025-09-19 기준으로 `places` 테이블은 이미 다음 인덱스를 갖추고 있습니다.

- `idx_places_location` (GiST)
- `idx_places_lower_name` (btree 함수 인덱스)
- `idx_places_category` (GIN)

따라서 태스크 1.2는 추가 작업 없이 “정상”으로 확인되었습니다. 앞으로 DB 스키마를 변경할 때 위 인덱스가 유지되는지 항상 체크리스트에 포함하세요.


## 6. 참고: 왜 GiST, GIN, BTREE인가?

- **GiST (Generalized Search Tree)**: PostGIS의 공간 데이터에 최적화된 인덱스. 특정 범위 안에 있는 좌표를 빠르게 찾을 수 있습니다.
- **GIN (Generalized Inverted Index)**: 배열, JSONB 같은 복합 데이터를 빠르게 검색할 수 있도록 설계된 인덱스. 카테고리처럼 여러 값을 가진 컬럼에 적합합니다.
- **BTREE + lower(name)**: 문자열 비교 시 대소문자를 무시하거나 특정 함수 결과를 기준으로 정렬/검색할 때 사용합니다.

이 세 가지 인덱스 조합을 갖추면 PRD에서 요구하는 검색/필터 기능을 성능 저하 없이 구현할 수 있습니다.

---

궁금한 점이 생기면, 직접 `EXPLAIN ANALYZE`로 실행 계획을 확인하고 결과를 캡처하여 멘토에게 공유하세요. “왜 이 인덱스가 필요한가?”를 설명할 수 있을 때까지 연습하면, 이후 복잡한 최적화 작업에도 쉽게 적응할 수 있습니다.
