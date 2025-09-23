# 2.2 장소 검색 서비스 (캐시 + PostGIS) 구현 가이드

이 문서는 `/api/v1/places` 검색 로직에서 캐시(60초)와 PostGIS 질의를 결합하는 방법을 설명합니다. 처음 해당 로직을 접하는 주니어 개발자도 이 단계를 통해 캐시 전략과 Native SQL 호출 흐름을 이해할 수 있습니다.

---

## 1. 왜 필요한가?

- 동일 조건의 검색 요청이 짧은 시간 안에 반복되면 DB 부하가 커지므로 캐시가 필요합니다.
- 캐시 미스 시 거리 기준으로 정확한 결과를 계산하기 위해 PostGIS의 공간 함수를 사용해야 합니다.
- 응답이 빠르고 일관된 기준으로 정렬되어야 이후 커서/레이트리밋 기능이 의미를 가집니다.

---

## 2. 핵심 개념

| 항목 | 설명 | 참고 |
| --- | --- | --- |
| Redis 캐시 | 키: `place:search:lat=...:lng=...:radius=...:size=...:cursor=...` | `StringRedisTemplate` 사용 |
| TTL | 60초 | 단기 반복 요청에 대한 최적화 |
| PostGIS 질의 | `ST_DWithin`, `ST_Distance`를 사용해 거리 ASC 정렬 | `sql/import_initial_places.sql`, `PostGIS docs` |
| 결과 DTO | `PlaceSearchResponse` (List + nextCursor) | 2.3에서 커서 추가 예정 |

---

## 3. 구현 단계

1. **Repository 작성**
   - `NamedParameterJdbcTemplate` 사용
   - SQL 템플릿 예시:
     ```sql
     WITH user_point AS (
         SELECT ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography AS point
     )
     SELECT p.place_id,
            p.name,
            ST_Distance(p.location, up.point, true) AS distance_m
     FROM places p
     CROSS JOIN user_point up
     WHERE ST_DWithin(p.location, up.point, :radius, true)
     ORDER BY distance_m ASC, p.place_id ASC
     LIMIT :limit;
     ```
   - 후속 단계(커서, 필터)에서 조건을 확장할 수 있도록 코드 구조화

2. **서비스 로직**
   - 요청 파라미터에서 `radius`, `size` 기본값 적용 (300m, 20개)
   - 캐시 키 생성: lat/lng/radius/size/cursor/filters를 포함해 문자열로 구성
   - `StringRedisTemplate.opsForValue()`로 캐시 조회
   - 히트 시 JSON 역직렬화 후 반환
   - 미스 시 Repository 호출 → 결과 직렬화 후 캐시에 저장(TTL=60초)

3. **오류 처리**
   - 캐시 데이터 파싱 실패 시 삭제 후 미스 처리
   - `ObjectMapper.writeValueAsString` 실패 시 런타임 예외 처리 (로그 남기기 권장)

---

## 4. 테스트 가이드

- **캐시 hit**: Redis mock에서 JSON 반환 → Repository 호출 없음 → 동일 DTO 반환
- **캐시 miss**: Repository mocking → 결과 저장 → `ops.set` 호출 확인
- **기본값 검증**: radius/size가 `null`일 때 기본값이 적용되는지 확인

---

## 5. 완료 후 체크리스트

- [ ] `PlaceRepository` Native SQL 실행 정상
- [ ] `PlaceSearchService` 캐시 → PostGIS 흐름 구현 완료
- [ ] 단위 테스트 (`PlaceSearchServiceTest`) 성공
- [ ] `docs/RUA/tasks-prd-v1-점심-추천-mvp.md` 2.2 항목 완료 표시

이 과정을 마치면 커서/상한/레이트리밋 등 다음 단계 기능을 안정적으로 붙일 수 있는 기반이 마련됩니다.
