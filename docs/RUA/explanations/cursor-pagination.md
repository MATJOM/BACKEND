# 2.3 거리 커서 페이징 구현 가이드

점심 추천 MVP의 `/api/v1/places` 검색 API에서 커서 기반 페이지네이션을 구현하기 위한 배경과 단계별 작업 방법을 정리했습니다. 처음 접하는 주니어 개발자도 이 문서를 보면 0 → 100까지 따라갈 수 있도록 작성했습니다.

---

## 1. 왜 필요한가?

- 검색 결과를 거리 순으로 정렬할 때 **고정된 page/offset 방식**은 대량 데이터(9만+ 건)에서 성능이 떨어지고 중복/누락이 발생할 수 있습니다.
- 커서(`distance,lastId`)를 사용하면 이전 페이지의 마지막 위치 이후부터 이어서 조회할 수 있어 **일관성과 성능이 보장**됩니다.
- PRD 2.3에선 거리 ASC, `place_id` ASC 정렬을 기준으로 커서를 만들고, 클라이언트가 `nextCursor`를 그대로 다시 보내면 이어서 결과를 받을 수 있어야 합니다.

---

## 2. 핵심 개념

| 항목 | 설명 |
| --- | --- |
| 정렬 기준 | `distance_m ASC, place_id ASC` |
| 커서 문자열 | `"{distanceMeters}:{lastPlaceId}"` (예: `"123.45:98765"`) |
| 커서 파싱 | `distance`는 `double`, `lastId`는 `long`으로 파싱하고 형식 오류 시 400 Bad Request |
| 다음 커서 생성 | 현재 페이지 마지막 레코드의 거리/ID로 생성, 더 이상 데이터 없으면 `null` |

---

## 3. 사전 준비

1. `PlaceSearchResponse`에 `nextCursor` 필드가 있는지 확인 (`null`이면 마지막 페이지).
2. `PlaceRepository`가 정렬을 `distance ASC, place_id ASC`로 반환하는지 확인.
3. 기존 서비스가 Redis 캐시에서 DTO 전체를 직렬화/역직렬화하고 있으므로, 커서 계산 후 `nextCursor`까지 포함해 캐싱해야 함.

---

## 4. 단계별 구현 지시문

### Step 1. 커서 파서/검증 유틸 작성
- 새 클래스 예시: `CursorToken` (`distanceMeters`, `lastId`).
- 정규식 `^[0-9]+(\.[0-9]+)?:[0-9]+$` 기반 검증 후 split.
- 거리 값은 `Double.parseDouble`, ID는 `Long.parseLong`. NumberFormatException 발생 시 `DomainException` 혹은 400 Bad Request 반환.

### Step 2. Repository에 커서 조건 적용
- 기존 `WHERE ST_DWithin(...)` 뒤에 커서 조건 추가:
  ```sql
  AND (
      distance_m > :cursorDistance
      OR (distance_m = :cursorDistance AND p.place_id > :cursorLastId)
  )
  ```
- 커서가 없으면 위 조건을 생략할 수 있도록 동적 쿼리 작성 (NamedParameterJdbcTemplate 사용 시 `StringBuilder` 또는 두 버전을 준비).

### Step 3. 다음 커서 생성 로직
- 반환 리스트가 비어 있으면 `nextCursor = null`.
- 마지막 요소 `(lastDistance, lastId)`로 `String.format(Locale.US, "%.2f:%d", lastDistance, lastId)` 생성. 소수점 자릿수 정책은 캐시/재현성을 위해 고정.
- 정렬 기준과 동일하게 `distance ASC, place_id ASC`로 계산해야 커서가 일관됨.

### Step 4. 캐시 적용 시 유의 사항
- 캐시 키에 `cursor` 값을 포함하고 있으므로 기존 키 생성기를 재사용.
- 캐시 hit 시 Response 객체 안의 `nextCursor`가 함께 내려오는지 확인.

### Step 5. 예외 처리
- 커서 파싱 중 에러 → 400 (message: "cursor 형식이 올바르지 않습니다.").
- 클라이언트가 이전 결과에서 받은 `nextCursor`만 사용하면 정상 동작함을 docs에 명시.

---

## 5. 수용 기준 (AC)

- [ ] Given 유효한 커서 When `/api/v1/places` 호출 Then 결과가 중복 없이 이어지고 `nextCursor`가 새롭게 계산된다.
- [ ] Given 마지막 페이지 When 호출 Then `nextCursor=null`을 반환한다.
- [ ] Given 잘못된 커서 문자열 When 호출 Then 400 + 에러 메시지를 반환한다.

---

## 6. 테스트 체크리스트

1. **커서가 없을 때**: 첫 페이지가 거리 ASC + ID ASC 순으로 정렬되는지 검증.
2. **커서가 있을 때**: 이전 마지막 레코드 이후부터 시작하는지, 중복/누락 없는지 확인.
3. **마지막 페이지**: 반환 목록 크기 < size → `nextCursor`가 null.
4. **잘못된 커서**: `"abc"`, `"12:abc"`, 빈 문자열 등 → 400 에러.
5. **캐시 연동**: 동일 파라미터 + 커서 조합으로 재호출 시 캐시 응답 확인.

---

## 7. 문서/코드 후속 작업

- `docs/RUA/tasks-prd-v1-점심-추천-mvp.md`의 2.3 항목을 완료 후 체크.
- OpenAPI 문서 업데이트(커서 파라미터 설명, `nextCursor` 필드, 400 에러 예시).
- 필요 시 `docs/RUA/explanations/search-baseline.md`에 커서 기반 측정 결과를 추가.

---

이 문서를 참고해 커서 페이징을 구현한 뒤, 단위/통합 테스트와 문서 업데이트까지 완료하면 2.3 서브 이슈를 마칠 수 있습니다.
