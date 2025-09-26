# 3.1 룰렛 API(postRoulette) 구현 가이드

이 문서는 `/api/v1/recommendations/roulette` 엔드포인트를 구현하기 위해 필요한 배경지식과 단계별 작업 지시를 정리합니다. 처음 해당 기능을 담당하는 개발자도 0 → 100까지 따라갈 수 있도록 구성했습니다.

---

## 1. 왜 필요한가?

- 점심 추천 서비스는 동일 조건 요청이 짧은 시간 동안 반복될 수 있으며, 멱등성을 보장해야 중복 추천이나 race condition을 방지할 수 있습니다.
- PRD 3.1은 클라이언트가 반드시 `Idempotency-Key` 헤더를 포함해야 하며, 누락 시 400 또는 409 대응을 명확히 하도록 요구합니다.
- 이 단계에서는 컨트롤러 스켈레톤과 헤더 검증만 구현하고, 실제 추천 로직(3.2 이후)은 후속 작업에서 다룹니다.

---

## 2. 사전 지식 요약

| 항목 | 설명 | 참고 |
| --- | --- | --- |
| 공통 응답 포맷 | `ApiResponse` 구조를 사용 | `docs/공통응답_가이드.md` |
| 요청 검증 | Spring `@Validated`, `@RequestBody` DTO | Spring Boot Docs (Validation) |
| 멱등 헤더 규칙 | `Idempotency-Key` 헤더는 비어 있거나 200자 초과 등 비정상 값은 거절 | Stripe 등 멱등키 표준 참조 |
| 에러 코드 | 임시로 `INVALID_REQUEST_PARAM`/`IDEMPOTENCY_KEY_REQUIRED` (필요 시 신규코드 정의) | `ErrorCode` enum |

---

## 3. 요구사항 정리

1. HTTP `POST /api/v1/recommendations/roulette` 엔드포인트 생성.
2. HTTP 헤더 `Idempotency-Key`를 필수로 요구.
   - 비어있거나 공백만 있으면 400(Bad Request) + 에러 메시지 반환.
   - 헤더명 대소문자 구분 없음(`request.getHeader("Idempotency-Key")`).
3. 요청 본문은 후속 단계에서 정의되므로, 우선 단순 DTO(예: 추천 범위/필터) 또는 `@RequestBody Map<String,Object>`로 placeholder 처리 가능.
4. 응답은 `ApiResponse.ok()` 또는 placeholder 데이터를 반환.
5. 로깅/추적을 위해 `Idempotency-Key` 값을 MDC로 전달하는 것도 고려(옵션).

---

## 4. 단계별 작업 지시문

### Step 1. 패키지 및 컨트롤러 생성
- 경로: `src/main/java/com/matjom/matjom/recommendation/api/RouletteController.java`
- 애노테이션: `@RestController`, `@RequestMapping("/api/v1/recommendations")`
- 메서드: `@PostMapping("/roulette")`

### Step 2. 헤더 검증 유틸 작성
- 간단한 private 메서드를 두어 `String idempotencyKey = request.getHeader("Idempotency-Key")` 값을 확인.
- `StringUtils.hasText` 활용해 공백 문자열을 거부.
- 누락 시 `throw new DomainException(ErrorCode.INVALID_REQUEST_PARAM, "Idempotency-Key 헤더가 필요합니다.")` 등으로 처리.
- 필요하다면 `ErrorCode`에 `IDEMPOTENCY_KEY_REQUIRED` 항목을 신설하고 공통 메시지를 추가.

### Step 3. 요청 DTO 준비 (선택 사항)
- 이후 3.2에서 후보군 생성 로직이 필요하므로, `RouletteRequest` DTO를 미리 정의.
- 필드: `lat`, `lng`, `radius`, `filters`, 사용자 세션 ID 등(추후 확정). 현재는 최소 필수 필드만 정의하고 TODO로 남긴다.

### Step 4. 서비스 호출 스켈레톤
- `RouletteService` 인터페이스/클래스를 생성해 향후 로직을 구현할 준비를 한다.
- 현재 단계에서는 `rouletteService.recommend(requestDto)` 호출 후 임시 응답 DTO를 구성하거나 `ApiResponse.ok()` 로 placeholder 응답.

### Step 5. 테스트 작성
- `src/test/java/com/matjom/matjom/recommendation/RouletteControllerTest.java`(혹은 스프링 `@WebMvcTest`) 추가.
- 시나리오:
  1. 헤더 누락 → 400 + `Idempotency-Key 헤더가 필요합니다.`
  2. 헤더 존재 → 200 반환 (서비스는 mock).
- MockMvc 사용 시 `mockMvc.perform(post(...).header("Idempotency-Key", "abc"))` 형태로 테스트.

---

## 5. 확인 체크리스트

- [ ] `RouletteController`가 `/roulette` POST 요청을 처리한다.
- [ ] `Idempotency-Key` 헤더가 없거나 비어 있으면 400 응답.
- [ ] 정상 요청 시 `ApiResponse` 구조로 응답.
- [ ] 단위/슬라이스 테스트가 헤더 검증을 커버한다.
- [ ] TODO 주석 / 문서에 후속 작업(멱등 저장소 연동, 후보군 로직 등)을 명시했다.

---

## 6. 커밋 메시지 예시

```bash
feat: add roulette controller skeleton with idempotency guard
- require Idempotency-Key header on POST /api/v1/recommendations/roulette
- return ApiResponse placeholder via RouletteService stub
- add controller slice tests covering missing header scenario
- related to PRD 3.1 룰렛 API 스켈레톤
```

---

이 문서를 기반으로 3.1 구현을 진행한 뒤, 클라이언트와의 계약(OpenAPI 갱신 등)과 실제 추천 로직은 3.2 이후 단계에서 다룹니다.
