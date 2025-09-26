# 2.6 Places 검색 OpenAPI 스펙 갱신 가이드

이 문서는 `/api/v1/places` 검색 API의 OpenAPI 스펙을 최신 상태(커서 페이징 + 500 상한 + 레이트리밋)로 반영하기 위한 사전 지식과 작업 지시를 정리합니다. 해당 기능을 처음 접하는 개발자가 0에서 100까지 따라가도록 구성했습니다.

---

## 1. 왜 필요한가?

- PRD 2.6은 클라이언트와의 계약 문서를 최신 상태로 유지해 개발/QA가 동일한 규약을 참조하도록 요구합니다.
- 최근 구현된 2.3~2.5 기능(커서 페이징, 결과 상한 메타, 레이트리밋 429 응답)을 문서에 명시하지 않으면, 프론트엔드가 `nextCursor`나 `meta.reason`, `Retry-After` 헤더를 인지하지 못해 오류가 발생할 수 있습니다.
- 현재 레포에는 `docs/openapi/openapi-v1.yaml`이 아직 없으므로, 신규 생성 또는 스켈레톤부터 작성해야 합니다.

---

## 2. 사전 지식 요약

| 항목 | 설명 | 참고 |
| --- | --- | --- |
| 공통 응답 포맷 | `ApiResponse` 구조(`success`, `data`, `error`, `timestamp`) | `docs/공통응답_가이드.md` |
| 검색 요청 DTO | `PlaceSearchRequest`: `lat`, `lng`, `radius`, `size`, `cursor`, `filters` | `src/main/java/com/matjom/matjom/place/dto/PlaceSearchRequest.java` |
| 검색 응답 DTO | `PlaceSearchResponse`: `places[]`, `nextCursor`, `meta(reason, suggest)` | `src/main/java/com/matjom/matjom/place/dto/PlaceSearchResponse.java` |
| 레이트리밋 에러 | `RateLimitFilter`가 429와 `Retry-After`, `X-RateLimit-*` 헤더를 반환 | `src/main/java/com/matjom/matjom/common/ratelimit/RateLimitFilter.java` |
| 에러 코드 | 429 케이스는 `SEARCH_RATE_LIMIT_EXCEEDED` 사용 | `ErrorCode.SEARCH_RATE_LIMIT_EXCEEDED` |

---

## 3. 문서 구성 방안

1. **OpenAPI 파일 구조 결정**
   - 경로: `docs/openapi/openapi-v1.yaml` (없다면 새로 생성).
   - 기본 메타: `openapi: 3.0.3`, 서버 URL placeholder, 공통 `ApiResponse` 스키마 정의.
2. **Components 정의**
   - `schemas.PlaceSearchRequest`: 위도/경도 범위, `radius` 기본값 설명, `cursor` 정규식 안내.
   - `schemas.PlaceSearchResponse`: `places` 배열(`placeId`, `name`, `distanceMeters`), `nextCursor` 문자열, `meta` 객체(`reason`, `suggest`).
   - `schemas.PlaceSearchMetaReason`: enum으로 `too_many_results` 등 향후 확장 대비.
   - `schemas.ErrorResponse`: 공통 실패 구조(`success=false`, `error` 객체) 명시.
3. **/api/v1/places Path 작성**
   - `GET` 메서드에 쿼리 파라미터(`lat` 등)와 기본값 설명.
   - `200` 응답: 성공 예시(20건 이하), 500 상한 초과 예시(메타 포함) 두 가지 예시를 `oneOf` 또는 `examples`로 제공.
   - `400` 응답: 잘못된 파라미터(`cursor` 형식 오류) 예시.
   - `429` 응답: 레이트리밋 초과. `headers` 섹션에 `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining` 정의.
4. **예시(JSON Examples)**
  - 성공: `success=true`, `data`에 places/nextCursor/meta 포함, `timestamp`.
  - 20건 미만: `meta.reason="low_results"`와 안내 문구 예시 포함.
  - 500 초과: `meta.reason="too_many_results"`, `meta.suggest` 문구 포함.
   - 오류: `success=false`, `error.code`, `error.message`, `timestamp`.

---

## 4. 단계별 작업 지시문

### Step 1. 스켈레톤 작성/검증
- 기존 문서가 없다면 `openapi-v1.yaml`을 새로 만들고 최소한의 OpenAPI 헤더와 `components` 섹션을 구성합니다.
- VSCode OpenAPI lint 확장 또는 `redocly lint`(추후 CI 연동 예정)로 문법 검증이 가능하도록 주석을 남깁니다.

### Step 2. 스키마 정의
- `PlaceSearchRequest` 스키마에서 각 필드 정보, 타입, 제약 조건(`minimum`, `maximum`, `pattern`)을 명시합니다.
- `PlaceSearchResponse` 스키마는 `meta`를 `nullable`로 지정하고, `reason` 필드를 enum으로 한정합니다.
- 공통 응답 포맷을 위해 `ApiResponsePlaceSearch`(성공)와 `ApiResponseError`(실패)를 래핑 스키마로 정의하면 재사용이 쉽습니다.

### Step 3. 경로 문서화
- `GET /api/v1/places` 경로에 `operationId`, `summary`, `description`을 작성합니다.
- `parameters`에는 필수/옵션 구분, 기본값 설명, 예시 값을 추가합니다(예: `lat=37.5665`).
- `responses`에 상태 코드별 스키마/예시를 연결:
  - `200`: `content.application/json.schema` → `ApiResponsePlaceSearch`.
  - `200.examples`: `normal`, `tooManyResults`, `lowResults`.
  - `400`: `ApiResponseError` + 예시(`cursor` 형식 오류).
  - `429`: `ApiResponseError` + 헤더(`Retry-After`, `X-RateLimit-*`).

### Step 4. 메타/레이트리밋 설명 추가
- `description`이나 `x-notes` 필드에 간단히 500 상한 정책, 레이트리밋 정책을 언급해 클라이언트가 문서를 보고 이해할 수 있도록 합니다.

### Step 5. 문서 교차 검증
- 구현 코드와 스키마 필드명이 일치하는지 다시 확인합니다(`distanceMeters`, `nextCursor`, `meta.reason`).
- 테스트 데이터나 실제 API 호출 결과(JSON)를 참고해 예시가 현실적이도록 작성합니다.

### Step 6. 버전 관리
- 문서 수정이 완료되면 PR 설명이나 커밋 메시지에 "OpenAPI v1 업데이트"와 함께 관련 태스크 번호(2.6)를 명시합니다.
- 추후 2.10~ 등 다른 API가 추가될 때 동일한 구조를 재사용할 수 있도록 컴포넌트 네이밍을 일관성 있게 유지합니다.

---

## 5. 확인 체크리스트

- [ ] `docs/openapi/openapi-v1.yaml` 파일이 존재하며 lint를 통과한다.
- [ ] 요청 파라미터 타입/범위가 DTO와 일치한다.
- [ ] 성공 응답에 `nextCursor`와 `meta`를 문서화했다.
- [ ] `Retry-After`/`X-RateLimit-*` 헤더가 429 응답에 명시됐다.
- [ ] 400/429 예시가 실제 에러 포맷(`success=false`)과 일치한다.
- [ ] 문서 업데이트 내역을 PRD 태스크(2.6)와 연동했다.

---

## 6. 커밋 메시지 예시

```bash
feat(docs): update v1 places search OpenAPI spec
- document cursor pagination and meta reason fields
- add rate limit (429) responses with headers
 - provide examples for too_many_results/low_results guidance
- related to PRD 2.6 검색 API 문서화
```

---

문서를 숙지한 뒤, 사용자의 승인 후 실제 OpenAPI 파일 생성/수정 작업을 진행하면 됩니다.
