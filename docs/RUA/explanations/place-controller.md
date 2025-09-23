# 2.1 장소 검색 컨트롤러 스켈레톤 가이드

이 문서는 `/api/v1/places` 엔드포인트 스켈레톤을 구현하는 방법과 배경 지식을 정리합니다. 최초로 해당 기능을 개발하는 주니어 개발자도 이 단계를 통해 컨트롤러/DTO/Validation 구조를 이해할 수 있습니다.

---

## 1. 왜 필요한가?

- PRD 2.1에 따르면 장소 검색 API는 `GET /api/v1/places`로 제공되어야 합니다.
- 이후 단계(캐시, 커서, 레이트리밋 등)를 시행하기 위해서는 최소한 **입력 파라미터 검증**과 **공통 응답 포맷**이 갖춰진 스켈레톤이 필요합니다.

---

## 2. 준비 지식

| 항목 | 설명 | 참고 |
| --- | --- | --- |
| Spring MVC | `@RestController`, `@RequestMapping`, `@ModelAttribute` | Spring Boot Docs 3.x MVC 섹션 |
| Bean Validation | `jakarta.validation` 어노테이션, `@Valid`, `@Validated` | Hibernate Validator Docs |
| 공통 응답 | `ApiResponse.ok(...)` 패턴 | `docs/공통응답_가이드.md` |

---

## 3. 구현 단계

1. `PlaceController`
   - `@RestController`, `@RequestMapping("/api/v1/places")`
   - 메서드: `public ApiResponse<PlaceSearchResponse> getPlaces(@Valid @ModelAttribute PlaceSearchRequest request)`
   - 서비스 인젝션: 생성자 주입

2. `PlaceSearchRequest`
   - 필드: `lat`, `lng`, `radius`, `size`, `cursor`, `filters`
   - 검증: `@NotNull + @DecimalMin/-Max`, `@Positive`, `@Max(500)`, `@Pattern` 등
   - 헬퍼: `radiusOrDefault`, `sizeOrDefault`

3. `PlaceSearchResponse`
   - `List<PlaceSummary>` + `nextCursor`
   - `PlaceSummary` record: `placeId`, `name`, `distanceMeters`

4. 스켈레톤 서비스 (`PlaceSearchService`)
   - 임시로 빈 결과를 반환하거나 TODO 표시 후 다음 단계에서 구현

---

## 4. 테스트 체크

- 컨트롤러 레벨 통합 테스트는 2.2 이후 작성 예정이므로, 현재는 스프링 부트 애플리케이션이 정상 기동되는지와 Validation 에러 메시지를 체크하는데 초점
- `./gradlew test` 실행 시 빌드 성공 확인

---

## 5. 완료 후 문서/태스크 업데이트

- `docs/RUA/tasks-prd-v1-점심-추천-mvp.md`의 2.1 항목을 완료 처리
- PR 코멘트나 작업 노트에 검증 방식(예: `MockMvc` 테스트 계획) 기록

이 가이드를 따라 스켈레톤을 준비하면, 이후 캐시/커서/레이트리밋 기능을 차례로 구현할 수 있습니다.
