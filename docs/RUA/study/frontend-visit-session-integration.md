# 프런트엔드 가이드: 방문 세션 도메인 (`/api/v1/sessions` 계열)

사용자의 “방문 진행 상태”를 서버와 동기화해 도착 여부를 판정하고, 이후 권한(리뷰/룰렛 제한 등)을 부여하는 흐름입니다. 프런트 주니어도 아래 순서대로 따라 하면 구현할 수 있습니다.

---
## 1. 전체 플로우 요약
1. **세션 시작** – `POST /api/v1/sessions`  
   - 장소를 선택한 뒤 “방문 시작”을 누르면 호출. 30분 동안 `ACTIVE` 상태가 유지됩니다.
2. **위치 이벤트 전송** – `POST /api/v1/sessions/{id}/positions`  
   - 자동 모드일 경우 30초 주기로 GPS 위치를 전송. 서버가 자동 도착 여부를 판정합니다.
3. **수동 도착 확정(선택)** – `POST /api/v1/sessions/{id}/arrivals`  
   - 사용자가 직접 “도착”을 눌렀을 때 호출. 자동 판정이 지연될 때 대비.

모든 엔드포인트는 **JWT 인증**이 필요하며, 멱등 요구 헤더가 있는 경우 반드시 제공해야 합니다.

---
## 2. 공통 준비 체크리스트
| 항목 | 설명 |
|------|------|
| 인증 | `Authorization: Bearer <access_token>` 필수 |
| 멱등 헤더 | `Idempotency-Key` (세션 시작, 수동 도착에서 필수) |
| 위치 권한 | 위도/경도/정확도 값 확보 필요 |
| 타임스탬프 | `recordedAt` 필드에 ISO-8601 (예: `"2025-03-03T12:34:56+09:00"`) 포맷 사용 |

Idempotency-Key 규칙은 룰렛 API와 동일합니다. 200자 이하, 공백 제거, 재시도 시 같은 키 재사용.

---
## 3. 엔드포인트별 상세

### 3.1 세션 시작 – `POST /api/v1/sessions`
**헤더**
- `Authorization: Bearer ...`
- `Idempotency-Key: <unique>` (필수)

**요청 바디**
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `placeId` | long | ✅ | 방문 대상 장소 ID |
| `clientNote` | string | ⛔ (≤ 50자) | UI에서 추가 메모 전달 시 사용 |
| `clientMode` | string | ⛔ | `NAVIGATION`(디폴트) 또는 `MANUAL` |

```json
POST /api/v1/sessions
Authorization: Bearer eyJ...
Idempotency-Key: session-start-uuid
Content-Type: application/json

{ "placeId": 12345, "clientMode": "NAVIGATION" }
```

**응답**
```json
{
  "success": true,
  "data": {
    "sessionId": 9876,
    "state": "ACTIVE",
    "startedAt": "2025-03-03T12:34:56+09:00",
    "expiresAt": "2025-03-03T13:04:56+09:00",
    "replayed": false
  }
}
```
`replayed: true`면 60초 내 같은 요청이 재생된 것. 세션 ID는 이후 위치/도착 API에서 경로 변수로 사용합니다.

**에러 예시**
| 상황 | HTTP | ErrorCode |
|------|------|-----------|
| 멱등키 누락 | 400 | `IDEMPOTENCY_KEY_REQUIRED` |
| 이미 ACTIVE 세션 존재 | 409 | `SESSION_ALREADY_EXISTS` |
| 인증 누락 | 401 | `UNAUTHORIZED` |

---
### 3.2 위치 이벤트 – `POST /api/v1/sessions/{sessionId}/positions`
**헤더**
- `Authorization: Bearer ...`
- 멱등키 없음

**요청 바디**
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `latitude` | decimal | ✅ | -90 ~ 90 |
| `longitude` | decimal | ✅ | -180 ~ 180 |
| `accuracyMeters` | decimal | ⛔ | GPS 정확도. 30m 초과 시 dwell 일시 정지 |
| `mode` | string | ⛔ | 미입력 시 `NAVIGATION` |
| `recordedAt` | string(ISO) | ✅ | 이벤트 발생 시각 |

```json
POST /api/v1/sessions/9876/positions
Authorization: Bearer eyJ...
Content-Type: application/json

{
  "latitude": 37.5665,
  "longitude": 126.9780,
  "accuracyMeters": 5.2,
  "recordedAt": "2025-03-03T12:36:00+09:00"
}
```

**응답 주요 필드**
- `state`: 현재 세션 상태 (`ACTIVE`, `ARRIVED`, `EXPIRED` …)
- `dwellSeconds`: 30m 안에서 누적 체류 시간(초). 정확도 30m 초과 시 증가 멈춤.
- `accuracyPaused`: 정확도가 낮아 dwell이 멈춘 상태인지 여부.

---
### 3.3 수동 도착 – `POST /api/v1/sessions/{sessionId}/arrivals`
**헤더**
- `Authorization: Bearer ...`
- `Idempotency-Key` (필수)

**요청 바디**
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `latitude` | decimal | ✅ | 현재 위치 위도 |
| `longitude` | decimal | ✅ | 현재 위치 경도 |
| `accuracyMeters` | decimal | ⛔ | 정확도 |
| `requestedBy` | string | ✅ | “사용자” 등 도착을 누른 주체 |

제약사항:
- 세션 시작 후 **10분 이상, 60분 이하**일 때만 수동 도착 허용 (`ARRIVAL_TIME_INVALID` 에러).
- 장소 기준 **30m 이내**여야 함 (`ARRIVAL_DISTANCE_EXCEEDED` 에러).
- 이미 도착/만료된 세션이면 409 (`SESSION_ALREADY_INACTIVE`).

응답은 세션 ID, 상태 `ARRIVED`, 도착 시각, `replayed` 여부 등을 돌려줍니다.

---
## 4. 프런트 구현 팁
1. **Idempotency-Key 전략**
   - 세션 시작/수동 도착마다 새로운 UUID 생성. 네트워크 재시도 시 동일 키 재사용.
2. **로컬 상태와 동기화**
   - `state`가 `ARRIVED`로 바뀌면 위치 전송 타이머를 종료하고, 도착 UI를 표시.
3. **정확도 경고 UI**
   - 응답 `accuracyPaused = true`면 “GPS 정확도가 낮아 도착 판정이 일시 중지” 메시지 제공.
4. **만료 처리**
   - `expiresAt`을 기준으로 카운트다운 UI를 구성. 30분 초과 시 서버가 자동으로 `EXPIRED` 상태로 전환한다.

---
이 문서를 따르면 프런트 주니어 개발자도 방문 세션 관련 API를 정확히 호출하고, 응답 값에 맞춘 UX 흐름을 구현할 수 있습니다.
