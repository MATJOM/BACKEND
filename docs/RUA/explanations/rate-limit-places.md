# 2.5 장소 검색 레이트리밋 구현 가이드

이 문서는 `/api/v1/places` 검색 API에 **10요청/10초(user & fallback IP)** 레이트리밋을 적용하기 위해 필요한 사전 지식과 작업 지시를 정리했습니다. 해당 기능을 처음 구현하는 개발자도 본 문서를 따라 0에서 100까지 완료할 수 있습니다.

---

## 1. 왜 필요한가?

- 검색 API는 위치 기반 서비스 특성상 트래픽이 급증할 수 있으며, 캐시 미스 시 DB 부하가 커집니다.
- 악의적인 반복 호출이나 버그로 인해 초당 다수 요청이 발생하면 PostGIS/Redis가 병목이 됩니다.
- PRD 2.5 요구사항: **사용자 식별자(userId)**가 있으면 우선으로, 없으면 **IP** 기준으로 10초 동안 10회까지 허용하고, 초과 시 429와 `Retry-After` 헤더 값을 제공해야 합니다.

---

## 2. 사전 지식 요약

| 항목 | 설명 | 참고 |
| --- | --- | --- |
| Redis INCR | 단일 키에 대한 카운터 + TTL로 토큰을 관리(슬라이딩 윈도우 근사) | Spring Data Redis `StringRedisTemplate` |
| Spring Filter | 요청 전 단계에서 레이트리밋을 확인하고 차단 | `OncePerRequestFilter` |
| 키 스킴 | `rl:{route}:{scope}:{userId|ip}` 형태로 user/IP 모두 차단 가능 | 본 문서 4단계 참고 |
| 응답 헤더 | 429 응답 시 `Retry-After`, 정상일 때 `X-RateLimit-Remaining` 등 제공 | RFC 6585, 7231 |

---

## 3. 구현 전 체크리스트

1. **Spring Data Redis**: `StringRedisTemplate`이 이미 설정되어 있어야 하며, 해당 템플릿을 이용해 `INCR`, `EXPIRE`, `TTL`을 호출합니다.
2. **보안 컨텍스트**: 사용자 인증이 완료된 상태라면 요청에서 userId를 가져올 수 있는 헬퍼 (`SecurityContextHolder`, `X-User-Id` 헤더 등)가 필요합니다.
3. **키 스킴**: PRD 메모에 따라 `rl:{route}:{scope}:{userId|ip}` 패턴을 사용합니다.
4. **만료 정책**: 윈도우(10초)만큼 TTL을 설정하여 일정 시간 후 카운터가 자연히 정리되도록 합니다.

---

## 4. 단계별 작업 지시문

### Step 1. RedisSearchRateLimiter 구현
- `RedisSearchRateLimiter`는 `StringRedisTemplate`을 주입 받아 `INCR`로 카운터를 증가시킵니다.
- 첫 호출이면 `EXPIRE`를 걸어 윈도우(10초) 동안 유지합니다.
- 허용 시 남은 토큰 수를 반환하고, 한도를 넘으면 TTL을 읽어 `Retry-After` 계산 후 차단 결과를 반환합니다.
- 다중 키(user + ip)를 처리하기 위해 `rollback` 메서드를 제공하여 이전에 소비한 토큰을 되돌릴 수 있게 합니다.

### Step 2. RateLimitFilter 작성
- `OncePerRequestFilter`를 상속받아 GET `/api/v1/places`에만 적용합니다.
- UserId는 `X-User-Id` 헤더(또는 request attribute)에서 추출하고, 항상 IP 키(`X-Forwarded-For` → `request.getRemoteAddr`)도 추가합니다.
- 모든 키에 대해 토큰 소비를 시도하고, 어느 하나라도 실패하면 이전 소비분을 `rollback` 후 429 응답을 반환합니다.
- 성공 시 `X-RateLimit-Limit`, `X-RateLimit-Remaining` 헤더를 내려줍니다.

### Step 3. Filter 등록 및 순서 조정
- `SecurityConfig`에 `addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)`를 호출해 인증 필터 앞단에서 선제 차단합니다.

### Step 4. 응답 포맷
- 차단 시 `ApiResponse.error(ErrorCode.SEARCH_RATE_LIMIT_EXCEEDED, ...)`로 본문을 생성합니다.
- `Retry-After` 헤더는 TTL(초 단위)을 사용하며, 최소 1 이상이 되도록 보정합니다.

### Step 5. 구성 옵션 외부화
- `application.yml`에 `ratelimit.search.capacity`와 `ratelimit.search.window-seconds`를 추가해 환경마다 손쉽게 조정할 수 있습니다.

---

## 5. 테스트 전략

1. **단위 테스트**: `RedisSearchRateLimiterTest`에서 허용/차단/롤백/TTL 계산을 Mockito로 검증합니다.
2. **필터 테스트**: `RateLimitFilterTest`에서 user+ip 키 처리, 429 응답, 비적용 경로 등을 확인합니다.
3. **경계 케이스**:
   - 동일 요청이 10회까지 허용되고 11번째에서 429 발생하는지
   - userId가 없는 비로그인 요청이 IP 기준으로 차단되는지
   - `Retry-After` 값이 TTL과 일치하는지(최소 1초)

---

## 6. 문서/태스크 업데이트 플로우

1. 구현 전: 본 문서를 숙지하고 신규 클래스/설정을 설계합니다.
2. 구현 중: TODO/필요 결정사항(예: userId 추출 방식)을 문서에 주석 또는 추가 섹션으로 기록합니다.
3. 구현 완료 후: 2.5 항목을 `[x]` 처리하고, 본 문서 하단의 "변경 요약" 섹션에 실제 구현 내용을 추가합니다.

---

## 7. 변경 요약 섹션(구현 완료 후 업데이트)

- 구현 완료: 2025-02-27 (MVP 기준)
  - `src/main/java/com/matjom/matjom/common/ratelimit/RateLimitConfig.java` – Redis 기반 검색 레이트리밋 빈 등록
  - `src/main/java/com/matjom/matjom/common/ratelimit/RedisSearchRateLimiter.java` – `INCR + EXPIRE` 기반 토큰 관리 및 롤백 지원
  - `src/main/java/com/matjom/matjom/common/ratelimit/RateLimitFilter.java` – user/IP 동시 키 처리, 429 응답 포맷 구현
  - `src/main/java/com/matjom/matjom/common/security/SecurityConfig.java` – 레이트리밋 필터 체인에 등록
  - `src/test/java/com/matjom/matjom/common/ratelimit/RedisSearchRateLimiterTest.java` – 허용/차단/롤백 시나리오 검증
  - `src/test/java/com/matjom/matjom/common/ratelimit/RateLimitFilterTest.java` – 필터 레벨 동작 검증

---

이 지침을 기반으로 2.5 레이트리밋 구현을 시작하면, 앞선 2.4 상한 처리와 결합해 검색 API의 안정성을 크게 높일 수 있습니다.
