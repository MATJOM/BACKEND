# 서브 이슈 #24-4: feat(session) 위치 정확도 가드

## 배경/문제 정의
- GPS accuracy가 높을 때 dwell 계산이 왜곡되어 잘못된 도착 판정이 발생한다.
- 정확도 가드 로직이 없어 30m 이상 오차가 있어도 누적 시간이 계속 올라간다.

## 사용자 스토리
As 현장 운영 담당자,
I want 정확도가 낮은 위치 이벤트가 dwell 계산에서 제외되길 원한다
so that 잘못된 도착 판정과 고객 문의를 줄일 수 있다.

## 범위 (In/Out)
**In**
- accuracy > 30m 이벤트 처리 정책 구현
- GeoFenceEvaluator와 연동: pause/resume 플래그 관리
- 응답에 정확도 관련 정보 제공 (`accuracyPaused`)

**Out**
- dwell 자체 계산(4.3)
- 타임아웃, 수동 도착 처리

## 수용 기준 (AC)
- [ ] Given accuracy가 35m인 이벤트가 들어오면, Then dwell이 증가하지 않고 `accuracyPaused=true` 로 응답한다.
- [ ] Given pause 상태에서 accuracy ≤ 30m 이벤트가 들어오면, Then dwell 계산이 재개된다.
- [ ] Given pause가 60초 이상 지속되면, Then 경고 로그를 남긴다.

## API/계약 영향
- 위치 응답 DTO에 `accuracyPaused` 필드 추가 (boolean)
- 이벤트 로깅 시 accuracy 정보 포함

## 비기능 요구사항 (NFR)
- 정확도 가드 로직은 GeoFenceEvaluator와 분리된 컴포넌트로 테스트 가능해야 한다.
- 로그에는 accuracy 값, 좌표, 세션 ID 를 반드시 남긴다.

## 의존성/리스크
- 클라이언트가 accuracy 정보를 보내지 않을 경우 기본값 처리 정책 필요
- pause 상태에서의 타임아웃 계산 방식 정합성 검토 필요
