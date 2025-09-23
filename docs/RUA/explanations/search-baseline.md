# 장소 검색 성능 베이스라인 (샘플 1k)

태스크 **1.4 샘플 데이터 1k건 적재 및 질의 p95 베이스라인 캡처** 결과를 정리한 문서입니다. 목표는 이후 튜닝 시 비교할 수 있도록 기준 데이터를 남기는 것입니다.

---

## 1. 데이터 셋업

1. `./scripts/import_initial_places.sh docs/initial_data_set_need_change_provider_id.csv`
2. 로드 후 레코드 수 확인:
   ```sql
   SELECT COUNT(*) FROM places;      -- 97,156
   SELECT COUNT(*) FROM staging_initial_places; -- 97,156
   ```

## 2. p95 거리 기준

- 기준 지점: 강남역(경도 127.0276, 위도 37.4979)
- 쿼리: 상위 1,000개 장소를 거리 오름차순으로 정렬 후 `percentile_cont(0.95)` 계산

```sql
WITH ranked AS (
    SELECT ST_Distance(location::geography, ST_SetSRID(ST_MakePoint(127.0276, 37.4979), 4326)::geography) AS distance_m
    FROM places
    ORDER BY distance_m ASC
    LIMIT 1000
)
SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY distance_m) FROM ranked;
```

- 결과: **559.13 m**

## 3. EXPLAIN ANALYZE 요약

### 3.1 거리 정렬 1000건 (현재 geometry 컬럼)

- 실행 시간: 약 **313 ms**
- 실행 계획: `Parallel Seq Scan` + `top-N heapsort` (GiST 인덱스가 geometry → geography 캐스팅 때문에 활용되지 않음)
- 향후 1.1 태스크(geography 전환) 완료 시 인덱스 사용 여부 재측정 필요

### 3.2 반경 300 m 필터

- 실행 시간: 약 **257 ms**
- 계획: `Parallel Seq Scan`
- 반환 레코드: 320건

### 3.3 반경 3 km 필터

- 실행 시간: 약 **270 ms**
- 반환 레코드: 10,647건 (전체 데이터의 ~11%)

> 현재는 geometry 컬럼을 `::geography`로 변환해 사용하므로 Seq Scan이 발생합니다. 1.1 태스크에서 컬럼 타입을 `geography(Point,4326)`로 바꾸고 인덱스를 재생성하면 GiST 인덱스를 사용할 수 있습니다. 이후 동일 쿼리를 재측정하여 개선치를 기록하세요.

## 4. 권장 다음 단계

1. 1.1 태스크 완료 후 본 문서에 최신 실행 계획과 실행 시간을 덮어씌워 비교합니다.
2. 캐시/커서 로직을 구현한 뒤에는 API 레벨에서 p95 응답 시간을 측정하고 갱신합니다.
3. CI 환경에서 자동으로 성능 회귀를 감지할 수 있는 간단한 스모크 테스트(`EXPLAIN (ANALYZE, BUFFERS)`)를 고려합니다.

---

이 문서를 기반으로 앞으로의 최적화 결과를 누적 기록해 주세요.
