# 프런트엔드 가이드: 인증·인가 도메인 (`/api/auth/*`)

백엔드에 JWT 기반 인증이 추가되었습니다. 이 문서는 프런트 주니어가 로그인부터 토큰 재발급까지 모든 흐름을 이해하고 구현할 수 있도록 단계별로 정리했습니다.

---
## 1. 용어 정리
- **Access Token**: 모든 보호된 API 호출 시 `Authorization: Bearer ...`로 전송. 짧은 만료(기본 15분).
- **Refresh Token**: 서버 Redis에 사용자별로 저장. 재발급 시 필요하며, 유출 방지를 위해 **클라이언트 저장소 주의**(HTTP-only 쿠키 or 보안 영역 권장).
- **Token Blacklist**: 로그아웃 시 Access Token을 블랙리스트에 저장해 즉시 무효화.

---
## 2. 전체 흐름 요약
1. **회원가입** (`POST /api/auth/signup`)  
   - 이메일·비밀번호·이름 입력 → 계정 생성 + 즉시 로그인 응답.
2. **로그인** (`POST /api/auth/login`)  
   - 이메일/비밀번호로 인증 → Access/Refresh 토큰 발급.
   - 실패 5회 시 5분 차단(`LOGIN_TOO_MANY_ATTEMPTS`).
3. **API 호출**  
   - 모든 보호 API는 `Authorization: Bearer <access>` 헤더 필요.
4. **재발급** (`POST /api/auth/reissue`)  
   - Access 만료 시 Refresh로 재발급. 실패하면 재로그인 요구.
5. **로그아웃** (`POST /api/auth/logout`)  
   - Access 블랙리스트 + Refresh 삭제.
6. **OAuth 로그인 (Google)** (`POST /api/auth/oauth/google/callback`)  
   - 프런트에서 Google ID 토큰 취득 후 서버에 전달 → 동일한 토큰 응답 구조.

---
## 3. 요청/응답 상세

### 3.1 회원가입 – `POST /api/auth/signup`
```json
{
  "email": "user@example.com",
  "password": "plain-text",
  "name": "테스터"
}
```
**응답 (`LoginResponse`)**
```json
{
  "success": true,
  "data": {
    "userId": "uuid",
    "email": "user@example.com",
    "name": "테스터",
    "accessToken": "Bearer ...",
    "refreshToken": "refresh-token..."
  }
}
```
- Access Token은 `Authorization` 헤더 값으로 바로 사용.
- Refresh Token은 안전한 저장소(예: 쿠키)로 보관.

### 3.2 로그인 – `POST /api/auth/login`
요청 바디는 회원가입과 동일 (`email`, `password`).  
응답도 `LoginResponse`. 실패 시 참고:
| ErrorCode | 의미 | 프런트 대응 |
|-----------|------|-------------|
| `INVALID_CREDENTIALS` | 이메일/비밀번호 불일치 | 에러 메시지 표시 |
| `LOGIN_TOO_MANY_ATTEMPTS` | 5회 연속 실패 | `message` 필드에 남은 대기 시간 안내 |

### 3.3 로그아웃 – `POST /api/auth/logout`
- 헤더 `Authorization: Bearer <access>` 필요.
- 응답 본문 없음. 성공 후 로컬 저장 토큰 삭제.

### 3.4 토큰 재발급 – `POST /api/auth/reissue`
```http
POST /api/auth/reissue
Authorization: Bearer <만료된 access 가능>
Content-Type: application/json

{ "refreshToken": "<refresh token>" }
```
- 유효한 Refresh Token이면 새로운 Access/Refresh 반환 (`LoginResponse`).
- Access Token은 블랙리스트에 등록되므로 기존 토큰 사용 불가.
- 실패 경우
  - `REFRESH_TOKEN_NOT_FOUND`: Redis에 저장된 토큰이 없음 (탭 종료/탈취 등)
  - `INVALID_TOKEN`: Refresh Token 위조 or 타입 오류
  - `UNAUTHORIZED`: Access Token이 블랙리스트에 있는 경우

### 3.5 Google OAuth – `POST /api/auth/oauth/google/callback`
- 요청 바디: `{ "idToken": "<Google ID token>" }`
- 서버가 Google에 검증 후, 동일한 `LoginResponse` 반환.
- 신규 사용자일 경우 자동 회원가입 (provider=GOOGLE).

---
## 4. 프런트 상태 관리 전략
1. **로그인 후 저장**
   - Access Token: 메모리 or Secure Storage (브라우저라면 메모리 + HTTP-only 쿠키 조합 권장).
   - Refresh Token: 로컬 저장소에 plaintext로 보관하지 말 것. 가능한 경우 서버 측 세션 or Secure cookie로 이관.
2. **API 호출 공통 모듈**
   - 401/`UNAUTHORIZED` → 재발급 시도 → 실패 시 로그인 페이지로 이동.
   - 429/`LOGIN_TOO_MANY_ATTEMPTS` → 서버 메시지를 사용자에게 그대로 전달.
3. **동시 기기 고려**
   - 로그아웃 시 Refresh Token이 삭제되므로 다른 기기에서도 재발급이 실패 → 재로그인 UX 안내.

---
## 5. 샘플 시나리오 (React 예시)
```ts
async function login(email: string, password: string) {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const { data } = await res.json();
  tokenStore.saveAccess(data.accessToken);
  tokenStore.saveRefresh(data.refreshToken);
}

async function secureFetch(url: string, options: RequestInit = {}) {
  const access = tokenStore.getAccess();
  const res = await fetch(url, {
    ...options,
    headers: {
      ...(options.headers || {}),
      Authorization: access,
    },
  });
  if (res.status === 401) {
    const refreshed = await reissue();
    if (!refreshed) throw new Error('Session expired');
    return secureFetch(url, options); // 재시도
  }
  return res;
}
```

---
## 6. 체크리스트 마무리
- [ ] 회원가입/로그인 폼에서 응답 `LoginResponse` 처리 완료
- [ ] 모든 보호 API 호출에 `Authorization` 헤더 설정
- [ ] Access 만료 시 자동 재발급 처리 (`/auth/reissue`)
- [ ] 로그아웃 시 토큰/스토리지 완전히 비우기
- [ ] Google OAuth 연동 시 ID 토큰을 서버에 전달하는 로직 구현

이 가이드를 숙지하면 프런트 주니어도 새로운 인증 시스템 위에서 안전하고 일관된 UX를 제공할 수 있습니다.
