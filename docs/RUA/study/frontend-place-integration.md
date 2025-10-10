# 프런트엔드 가이드: 장소 검색 도메인 (GET `/api/v1/places`)

## 1. 목표 & 전체 흐름
- **목표**: 사용자의 현재 위치를 기준으로 반경 내 음식점을 거리 순으로 조회하고, 커서 기반으로 다음 페이지를 이어받는다.
- **전체 플로우**
  1. 사용자가 위치 권한을 허용하면 위도(lat)·경도(lng)를 확보한다.
  2. 쿼리 파라미터를 구성해 `GET /api/v1/places` 호출.
  3. 응답의 `places` 배열을 리스트/지도에 뿌리고, `nextCursor`가 있으면 “더보기” 요청에 활용한다.
  4. `meta.reason` 값이 있을 경우 UX 메시지를 보여준다(예: 결과가 너무 많음 → 필터 제안).

## 2. 준비 체크리스트 (주니어 개발자 0→100)
1. **인증 토큰**  
   - 백엔드 시큐리티 정책상 `/api/v1/places`는 인증 필요. 로그인 후 받은 `Authorization: Bearer <access_token>`을 요청마다 포함해야 한다.
2. **위치 권한 처리**  
   - 브라우저 `navigator.geolocation` 또는 네이티브 SDK로 위·경도를 가져온다. 실패 시 대체 좌표(회사 주소 등)를 정해 둘 것.
3. **Idempotency-Key 불필요**  
   - GET 요청이므로 멱등 헤더 필요 없음. (POST 계열에서만 요구)
4. **쿼리 파라미터 직렬화**  
   - 숫자는 소수점 그대로 전달하되, `cursor` 값은 서버에서 받은 문자열을 가공하지 말고 그대로 사용.

## 3. 요청 스펙
| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| `lat` | double | ✅ | - | 검색 기준 위도 (-90 ~ 90) |
| `lng` | double | ✅ | - | 검색 기준 경도 (-180 ~ 180) |
| `radius` | double | ⛔ | 300.0 | 탐색 반경(미터). 0보다 커야 함. 너무 큰 값은 400 에러 후 안내 메시지 |
| `size` | int | ⛔ | 20 | 한 페이지 결과 수. 최대 500 |
| `cursor` | string | ⛔ | 없음 | 다음 페이지 토큰. 서버 응답의 `nextCursor`를 그대로 재사용 |
| `filters` | string | ⛔ | 없음 | 예약된 필드(카테고리/키워드). 현재는 비워 두고 최대 200자 제한 |

예시 요청:
```
GET /api/v1/places?lat=37.5665&lng=126.9780&radius=400&size=30
Authorization: Bearer eyJhbGciOi...
```

## 4. 응답 구조
```json
{
  "success": true,
  "data": {
    "places": [
      {
        "placeId": 123,
        "name": "맛있는 김밥",
        "distanceMeters": 112.4,
        "latitude": 37.5670,
        "longitude": 126.9783
      }
    ],
    "nextCursor": "112.4:123",
    "meta": {
      "reason": "too_many_results",
      "suggest": "반경을 300m로 줄여보세요."
    }
  }
}
```
- `nextCursor`가 `null`이면 더 이상 페이지 없음.
- `meta.reason` 주요 값
  - `"too_many_results"`: 결과가 500개를 초과. 반경 축소 or 필터 필요.
  - `"low_results"` (향후): 결과가 적음. 반경 확대 안내.

## 5. 에러 & 예외 처리
| 상황 | HTTP | ErrorCode | 대응 전략 |
|------|------|-----------|-----------|
| 유효하지 않은 위도/경도/반경 | 400 | `INVALID_REQUEST_PARAM` 등 | 사용자 입력 재확인 및 안내 |
| 인증 누락/만료 | 401 | `UNAUTHORIZED` | 로그인 토큰 갱신 후 재시도 |
| 레이트리밋(추후) | 429 | `RATE_LIMIT_EXCEEDED` | `Retry-After` 헤더 확인 후 딜레이 |

## 6. 실전 팁
1. **지도 연동**  
   - `distanceMeters`는 서버 계산값이므로 별도 계산 없이 UI에 사용하기.
2. **페이지네이션**  
   - “더보기” 버튼 클릭 시 마지막 응답의 `nextCursor`를 붙여 재요청 → 리스트에 append.
3. **후속 액션(룰렛 연동)**  
   - 이 API에서 받은 `lat/lng/radius/categories` 정보를 그대로 룰렛 API에 전달해 일관된 후보 풀을 만들 수 있다.
4. **UX 문구 처리**  
   - `meta.suggest`는 바로 노출 가능한 문장. 번역이 필요하면 FE에서 i18n hook 제공.

---
이 문서를 완독하면 프런트 주니어도 `/api/v1/places`를 호출해 위치 기반 음식점 목록을 구현하고, 커서 기반 페이지네이션과 UX 가이드를 연동할 수 있습니다.
