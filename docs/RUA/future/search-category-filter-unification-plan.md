# Place Search & Roulette Category Filter Unification Plan

## 1. Background
- 2.x 장소 검색 API(`/api/v1/places`)는 `filters` 문자열을 아직 사용하지 않아 모든 결과를 반환한다.
- 3.x 룰렛 추천 API(`/api/v1/recommendations/roulette`)는 `categories` 리스트를 받아 Postgres 배열 연산(`&&`)으로 필터링을 수행한다.
- 프론트엔드 요구 사항: 카테고리를 `한식,양식,중식` 과 같이 콤마로 전달하고 두 API 모두 동일한 규약으로 동작하기를 원함.

## 2. Goals
1. 두 API가 동일한 카테고리 필터 입력 규약을 받아들이고 동일한 PostGIS/SQL 필터 로직을 사용한다.
2. 캐시(2.x) 및 멱등 해시(3.x) 계산 시 필터 문자열 차이로 인한 불일치를 방지한다.
3. OpenAPI, 가이드 문서, 테스트 케이스를 하나의 규약에 맞춰 업데이트한다.

## 3. Scope & Non-Goals
- ✅ Scope: DTO 변경, 파싱 유틸 추가, Repository 필터 공통화, 캐시/멱등키 정규화, 테스트/문서 업데이트.
- ❌ Non-goals: 카테고리 메타데이터 관리(예: DB CRUD), 다중 조건 필터(가격·거리 등) 추가.

## 4. Design Outline
### 4.1 입력 규약 통일
- 프론트: `filters=한식,양식` (2.x) / `categories=한식,양식` (3.x) 한 형태로 전달.
- 백엔드 파서: `CommaSeparatedCategories.parse(String)` → `List<String>` (trim, 빈 값 제거, 중복 제거, 순서 정렬).

### 4.2 DTO 리팩터링
- `PlaceSearchRequest`에 `List<String> categories` 필드를 추가하고, 기존 `filters`는 deprecated(추후 삭제 경로 문서화).
- `RouletteRequest`는 내부에서 공통 파서를 사용하도록 수정.
- Validation: 최대 5개, 각 1~30자 (두 DTO 동일 규칙 적용).

### 4.3 Repository 레이어 통일
- `PlaceRepository.search(...)`에 `:categories` 파라미터 추가.
  ```sql
  AND (:categories IS NULL OR p.category && :categories)
  ```
- `PlaceRepository.findRouletteCandidates(...)`와 동일 로직을 사용하되, SQL 중복을 줄이기 위해 공통 SQL 스니펫 상수 도입을 검토.

### 4.4 캐시/멱등 처리 정규화
- 2.x 캐시 키: `filters=` 부분을 파싱된 정렬 리스트 기반의 문자열(예: `한식|양식`)로 구성.
- 3.x 멱등 해시: 요청 직렬화 전에 DTO categories 필드는 항상 정렬+중복 제거된 상태여야 함.

### 4.5 테스트 전략
- `PlaceSearchServiceTest`
  - 카테고리 필터 적용/미적용 케이스 추가.
  - 캐시 키에 동일 카테고리 순서로 접근 시 캐시 히트 확인.
- `RouletteServiceTest`
  - 카테고리 필터 적용 시 후보가 줄어드는지 검증.
  - seed 기반 추천 + 멱등 재생이 동일 결과를 반환하는지 재확인.
- Repository 단위 테스트(또는 통합 테스트)에서 SQL 필터 동작 확인 (Postgres Testcontainer 권장).

## 5. Implementation Steps
1. **Utility**: `CategoryFilters` (parse, normalize, toRedisKey helper).
2. **DTO 업데이트**
   - `PlaceSearchRequest`에 리스트 필드 추가 + 생성자/메서드 업데이트.
   - `RouletteRequest`가 공통 유틸을 사용하도록 변경.
   - 기존 `filters` 문자열은 일시적으로 파서 진입점으로 두고, 추후 제거 로드맵 명시.
3. **Service / Repository**
   - `PlaceSearchService`에서 파싱된 리스트를 Repository에 전달.
   - `PlaceRepository.search` SQL 수정/파라미터 추가.
4. **캐시/멱등키 정규화**
   - 캐시 키 빌더, `computeRequestHash` 전처리에 정렬된 리스트 사용.
5. **테스트 & 문서**
   - 단위/통합 테스트 추가 및 기존 테스트 수정.
   - `docs/openapi/openapi-v1.yaml` 및 스터디 가이드 업데이트.
6. **릴리즈 노트**
   - 카테고리 필터 사용법 변경 사항을 README/변경 로그에 기록.

## 6. Risks & Mitigations
| Risk | Mitigation |
|------|------------|
| 기존 클라이언트가 `filters` 필드를 다른 용도로 사용 중일 수 있음 | 지원팀/클라이언트 공지, 과도기 동안 문자열 필드도 허용하되 내부에서 공통 파서 처리 |
| 캐시 키 변경으로 기존 캐시 미스 증가 | 배포 직후 짧은 캐시 파기 허용, 모니터링으로 영향 확인 |
| SQL 필터 추가로 성능 감소 가능성 | 카테고리 컬럼 GIN 인덱스 재확인, Testcontainer로 explain analyze 실행 |
| 파서가 공백/대소문자 처리 누락 시 불일치 발생 | 파서 유닛 테스트 작성(공백, 대소문자, 중복 케이스 포함) |

## 7. Follow-up
- `PlaceSearchRequest.filters` 제거 여부 검토 (다음 버전에서 API 계약 변경 공지 필요).
- 카테고리 메타데이터(설명/아이콘 등)를 별도 API로 노출할지 Product와 논의.
- 추후 다중 필터(가격대, 거리 등) 확장을 위한 DSL/파서 구조 사전 검토.

