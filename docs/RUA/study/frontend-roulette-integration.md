# 프런트엔드 가이드: 룰렛 추천 도메인 (POST `/api/v1/recommendations/roulette`)

## 1. 무엇을 만드는가?
- **목표**: 선택된 반경과 필터 조건을 기준으로 서버가 추출한 후보 중 “균등 확률”로 1개 음식점을 추천 받는다.
- **사용 시나리오**
  1. 장소 검색에서 받아온 lat/lng/radius/categories 정보를 그대로 사용.
  2. 사용자 인터랙션(“룰렛 돌리기” 버튼)을 누르면 API 호출.
  3. 서버가 추천한 장소 정보를 카드/팝업으로 노출. `meta.replayed` 값에 따라 “재생된 결과”인지 표시.

## 2. 준비 체크리스트
1. **인증 토큰**  
   - `/api/v1/recommendations/roulette`는 인증 필요. `Authorization: Bearer <access_token>` 헤더 필수.
2. **Idempotency-Key 필수**  
   - 헤더 `Idempotency-Key`가 없으면 400 에러.  
   - **규칙**
     - 200자 이하 문자열. 공백 제거 필요.
     - 같은 사용자/같은 요청 바디로 60초 내 재시도 시 *동일 결과*를 돌려받음.
     - 권장: `crypto.randomUUID()` 또는 타임스탬프 기반 유니크 키.
3. **요청 JSON 직렬화**  
   - `lat/lng/radius/categories`는 검색과 동일한 단위를 사용.
   - `seed`는 선택값(동일 seed → 동일 결과). 미지정 시 서버 난수.
4. **에러 대비**  
   - 400: 파라미터 검증 실패.  
   - 401: 토큰 만료.  
   - 409/429 등 추가 정책은 향후 도입 가능.

## 3. 요청 스펙
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `lat` | double | ✅ | 위도 (-90 ~ 90) |
| `lng` | double | ✅ | 경도 (-180 ~ 180) |
| `radius` | double | ⛔ (기본 300m) | 탐색 반경. 0보다 커야 함 |
| `categories` | string[] | ⛔ | 최대 5개. 문자열 길이 1~30 |
| `limit` | int | ⛔ (디폴트: 서비스 내부) | 후보 풀 최대 개수. 너무 크면 DB 부하 |
| `seed` | long | ⛔ | 동일 시나리오 재현용 |

예시 헤더 & 바디:
```http
POST /api/v1/recommendations/roulette
Authorization: Bearer eyJhbGciOi...
Idempotency-Key: roulette-20240909-uuid
Content-Type: application/json

{
  "lat": 37.5665,
  "lng": 126.9780,
  "radius": 400,
  "categories": ["korean", "lunch"],
  "limit": 100
}
```

## 4. 응답 구조
```json
{
  "success": true,
  "data": {
    "placeId": 321,
    "name": "돌림판김밥",
    "distanceMeters": 215.2,
    "categories": ["korean"],
    "latitude": 37.5671,
    "longitude": 126.9792,
    "meta": {
      "candidateCount": 42,
      "replayed": false
    }
  }
}
```
- `meta.candidateCount`: 룰렛 돌리기 직전 서버가 확보한 후보 수.
- `meta.replayed`: 멱등 재생 여부. `true`면 이전과 같은 결과를 반환했다는 의미 → FE에서 “같은 결과 재전송” UI 처리 가능.

## 5. 에러 코드 예시
| 상황 | HTTP | ErrorCode | 안내 문구 예시 |
|------|------|-----------|----------------|
| 멱등키 누락 | 400 | `IDEMPOTENCY_KEY_REQUIRED` | “오류가 발생했습니다. 잠시 후 다시 시도해 주세요.” |
| 멱등키 길이 초과 | 400 | `INVALID_REQUEST_PARAM` | “요청 헤더가 올바르지 않습니다.” |
| 인증 실패 | 401 | `UNAUTHORIZED` | 로그인 만료 시 재발급 UI 호출 |

## 6. 구현 팁
1. **Idempotency-Key 전략**
   - 버튼 클릭 시마다 새로운 키 생성. 로딩 중 동일 요청을 막고 싶다면 동일 키로 재시도 후 `meta.replayed`를 활용.
2. **로딩 UX**  
   - 서버는 Idempotency-Key 기준 60초 캐싱 → 동일 요청은 즉시 응답. 네트워크 오류 시 동일 키로 재시도하면 빠르게 복구 가능.
3. **Seed 지원**  
   - QA/디버깅 시 고정 seed를 넣으면 같은 장소가 계속 뽑힘. 사용자는 seed 미전달이 일반적.
4. **후속 액션**  
   - 추천 결과 클릭 시 “방문 세션 시작” API(`POST /api/v1/sessions`)와 연결해 도착 추적 흐름으로 자연스럽게 이어갈 수 있다.

---
이 문서를 통해 프런트 주니어 개발자는 룰렛 추천 API가 요구하는 헤더/바디 구조와 멱등 정책을 숙지하고, 사용자 경험에 맞는 호출 로직을 안전하게 구현할 수 있습니다.
