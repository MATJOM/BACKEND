# PR 제목 (Conventional Commits + 이슈번호)
<!-- 예시 -->
<!-- [#12] feat(auth): add signup API -->
<!-- [#45] fix(search): 커서 비교식 경계 오류 수정 -->
<!-- [#78] chore(build): Gradle 캐시 개선 -->
<!-- [#90] refactor(geo): distance 계산 유틸 정리 -->
<!-- [hotfix] fix(rate-limit): 토큰 버킷 임계치 상향 -->
<!-- [release] release: cut 1.3.0 -->

## 배경/문제(Background)
- (왜) 이 변경이 필요한가? 사용자/운영 관점으로 요약
- 관련 문서/이슈: PRD/노션/이슈 링크

## 주요 변경 사항(Changes)
- [ ] API/계약 변화: (엔드포인트/스키마/상태코드) — 변경 없으면 "없음"
- [ ] 도메인 로직/정책 반영 포인트
- [ ] 예외/에러코드 라인업(규격 준수)

### 스크린샷/응답 예시(선택)

## 테스트(Tests)
- [ ] 단위 테스트: 파일/케이스 요약
- [ ] 통합/계약 테스트: Testcontainers/REST Docs/OpenAPI
- [ ] 수동 검증: curl/스크립트

## 리스크/롤백(Risk & Rollback)
- [ ] 성능/캐시/N+1/메모리 회귀 없음
- [ ] 보안(인증/권한/시크릿) 이상 없음
- [ ] 롤백 방법/가드(플래그/트리거)

## 체크리스트(Checklist)
- [ ] 브랜치 이름 규칙 준수 (예: feature/123-auth-api)
- [ ] PR 크기 ±200 라인 목표 (가능하면 분할)
- [ ] CI green (build/test/lint)
- [ ] CODEOWNERS 승인 1+
- [ ] 로그 키 포함(traceId, sessionId 등)
- [ ] 문서 업데이트(CHANGELOG/README/운영 러너북)

## 이슈 링크(Issues)
- Closes #<메인 이슈>
- Refs: #<연관 이슈들>
