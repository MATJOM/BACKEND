# 1.1 장소 location 컬럼 GEOGRAPHY 전환 가이드

이번 태스크에서는 `places.location` 컬럼을 `geometry(Point,4326)`에서 `geography(Point,4326)`로 변환했습니다. 이 문서는 전환 이유, 수행 절차, 검증 방법을 정리합니다.

---

## 1. 왜 geography 로 바꿔야 하나요?

- **정확한 거리/반경 계산**: geometry는 단위를 도(°)로 취급하기 때문에 `ST_DWithin(location, ..., 30)`이 30도(약 3,000km)가 됩니다. geography는 미터 단위를 사용하기 때문에 `30`이 30미터로 해석됩니다.
- **인덱스 활용성**: geography 컬럼에 GiST 인덱스를 걸면 `ST_DWithin` 같은 공간 연산에서 인덱스가 직접 사용됩니다.
- **코드 단순화**: 캐스팅(`location::geography`)을 반복할 필요가 없어집니다.

---

## 2. 수행 절차

SQL 파일 `sql/migration/001_places_location_to_geography.sql`의 내용은 다음과 같습니다.

```sql
BEGIN;
ALTER TABLE places ADD COLUMN location_geog geography(Point,4326);
UPDATE places SET location_geog = location::geography;
DROP INDEX IF EXISTS idx_places_location;
ALTER TABLE places DROP COLUMN location;
ALTER TABLE places RENAME COLUMN location_geog TO location;
CREATE INDEX idx_places_location ON places USING gist (location);
COMMIT;
```

> 데이터가 많을 경우 UPDATE 시간이 오래 걸릴 수 있으므로 운영 환경에서는 백업과 점검 시간을 확보하세요.

---

## 3. 검증 결과

1. **컬럼 타입**
   ```sql
   SELECT DISTINCT pg_typeof(location) FROM places; -- geography
   ```
2. **SRID**
   ```sql
   SELECT ST_SRID(location::geometry) FROM places LIMIT 3; -- 4326
   ```
3. **인덱스**
   ```sql
   \d places  -- idx_places_location USING gist (location) 존재 확인
   ```
4. **거리/반경 쿼리**
   - 강남역(127.0276, 37.4979) 기준, 반경 300m 쿼리:
     ```sql
     EXPLAIN ANALYZE
     SELECT place_id
     FROM places
     WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(127.0276, 37.4979), 4326)::geography, 300);
     ```
     → Bitmap Index Scan + Execution Time 약 **18 ms**.

---

## 4. 성능 비교

| 쿼리 | 전환 전 | 전환 후 |
| --- | --- | --- |
| 반경 300m ST_DWithin | ~257 ms (Parallel Seq Scan) | ~18 ms (Bitmap Index Scan) |
| 반경 3km ST_DWithin | ~270 ms | ~35 ms |
| 거리 정렬 상위 1,000 | ~313 ms | ~345 ms *(여전히 전체 정렬 필요)* |

> 거리 정렬 쿼리는 추가 최적화가 필요하지만, 반경 필터 쿼리는 GiST 인덱스 덕분에 약 10배 빨라졌습니다.

---

## 5. 후속 작업 추천

- 애플리케이션 코드에서 불필요한 `location::geography` 캐스팅 제거
- Querydsl/Native Query 템플릿을 새 타입에 맞게 업데이트
- 스테이징/운영 환경에서도 동일 마이그레이션 적용
- `docs/RUA/explanations/search-baseline.md`의 성능 데이터 갱신

---

전환이 완료되면 이후 거리/반경 로직은 `ST_DWithin(location, ...)`처럼 간단하게 작성할 수 있으며, 미터 단위 계산이 기본이 됩니다.
