# 2.4 검색 결과 상한(500) 처리 가이드

본 문서는 `/api/v1/places` 검색 API에서 **500건 결과 상한**을 구현하기 위한 사전 지식과 단계별 작업 지시를 정리합니다. 처음 이 기능을 담당하는 개발자도 0에서 100까지 따라갈 수 있도록 설계되었습니다.

---

## 1. 왜 필요한가?

- 검색 범위를 넓게 지정하면 수천 건의 결과가 반환될 수 있으며, 이는 네트워크 지연과 클라이언트 렌더링 비용을 급증시킵니다.
- 기획 요구사항 2.4에 따라 501건 이상일 때는 정상 응답(HTTP 200)을 유지하되, 메타 정보로 "검색 범위를 좁히라"는 힌트를 제공해야 합니다.
- 커서 기반 페이지네이션과 조합 시, 상한을 넘는 결과는 다음 커서로 넘어가더라도 동일한 혼잡이 발생하므로 서버 차원에서 억제해야 합니다.

---

## 2. 사전 지식 요약

| 항목 | 설명 | 참고 |
| --- | --- | --- |
| `PlaceSearchService` | 검색 비즈니스 로직을 담당하며 캐시/커서 처리까지 수행 | `src/main/java/com/matjom/matjom/place/service/PlaceSearchService.java` |
| `PlaceSearchResponse` | 검색 응답 DTO. 커서와 결과 목록을 포함 | `src/main/java/com/matjom/matjom/place/dto/PlaceSearchResponse.java` |
| Redis 캐시 | 동일 파라미터 조회 시 60초 동안 응답을 캐싱 | `StringRedisTemplate` 기반 구현 |
| 커서 페이징 | `distance ASC, place_id ASC` 기준으로 `"distance:lastId"` 포맷 커서를 이용 | `docs/RUA/explanations/cursor-pagination.md` |

---

## 3. 구현 단계별 지시문

### Step 1. DTO 확장
- `PlaceSearchResponse`에 `Meta(reason, suggest)` 레코드를 추가합니다.
- 생성자 오버로드를 제공해 기존 2인자 생성 로직과 호환되도록 합니다.
- `meta`는 null 가능. 상한 초과 시에만 `Meta("too_many_results", "검색 반경을 줄이거나 필터를 추가해 주세요.")` 형식으로 채웁니다.

### Step 2. 서비스 로직 조정
- 요청 `size`는 그대로 캐시 키에 반영하지만, 실제 페이지 크기(`pageSize`)는 `min(size, 500)`으로 제한합니다.
- DB 조회 시 `fetchLimit = pageSize + 1` 대신 `MAX_RESULTS_PER_SEARCH + 1`(즉 501)로 조회해 상한 초과 여부를 감지합니다.
- 조회 결과가 500건을 넘으면:
  1. 응답 `places` 목록은 500건으로 잘라냅니다.
  2. `nextCursor`는 일반 규칙대로 설정하되, 요구사항상 즉시 축소를 안내해야 하므로 `Meta`에 reason/suggest를 채워 둡니다.
- 캐싱 전 `PlaceSearchResponse` 전체(메타 포함)를 직렬화해 Redis 에 저장합니다.

### Step 3. 보조 메서드 업데이트
- `trimToPage`는 음수/0 사이즈 입력을 방지하고, 상한 초과 상황에서도 500건까지만 반환하도록 `List.copyOf`를 사용합니다.
- `buildNextCursor`는 기존 로직을 유지하되, 빈 페이지(요청 size 0) 시 null을 반환하게 확인합니다.

### Step 4. 검증 & 예외 상황
- 상한 초과 시에도 HTTP 200을 반환해야 하며, 클라이언트는 meta.reason 값으로 안내 메시지를 표시합니다.
- 캐시된 응답이 존재한다면 동일한 Meta 정보가 함께 재사용됩니다.

---

## 4. 테스트 체크리스트

1. size=100 요청, DB가 501건을 반환 → 응답 `places` 크기=100, `meta.reason="too_many_results"`, `meta.suggest`는 빈 문자열이 아니어야 함.
2. size=500 요청, DB가 500건 반환 → `meta=null`, `nextCursor`는 null.
3. size=500 요청, DB가 501건 반환 → `places` 500건, `meta.reason` 설정.
4. 캐시 적중 시 메타 정보가 유지되는지 확인.

---

## 5. 완료 후 문서/태스크 업데이트

- `docs/RUA/tasks-prd-v1-점심-추천-mvp.md`의 2.4 항목을 `[x]`로 표시합니다.
- API 문서(`docs/openapi/openapi-v1.yaml`)에는 meta.reason/meta.suggest 필드를 추가해 클라이언트와 합의합니다.
- 추가 실험(예: 반경 축소 시 응답 회복)을 진행하면 `docs/RUA/explanations/search-baseline.md`에 메모합니다.

---

## 6. 참고 커밋 메시지 예시

```bash
feat: cap places search results at 500
- enforce 500 item limit and attach guidance meta
- extend PlaceSearchResponse with meta payload
- add unit tests covering too_many_results scenarios
- related to PRD 2.4 검색 결과 상한 구현
```

---

이 지침에 따라 구현을 완료한 뒤, PRD 2.5(레이트리밋)로 진행할 때 본 문서를 참고해 배경과 요구사항을 회고할 수 있습니다.
