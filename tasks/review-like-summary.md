# Review & Like 구현 변경 기록

## 1. 리뷰 도메인 슬림화
- **목적**: 팀장 지침에 따라 리뷰 상태와 필드를 최소화해 관리 복잡도를 줄이고 JWT 기반 사용자 정보 흐름에 집중.
- **주요 수정**
  - `Review`, `ReviewStatus`, `schema-postgres.sql`에서 숨김/플래그 관련 필드 제거 후 상태를 `ACTIVE/DELETED`만 사용하도록 정비.
  - `ReviewResponseDTO`를 `reviewId`, `reviewerName`, `placeName`, `text`, `createdAt` 중심으로 재구성하여 응답 가독성을 높임.
  - `ReviewRepository`는 실제 사용하는 조회/집계 메서드만 남겨 단순화.
- **로직 흐름**
  1. 리뷰 작성 시 엔티티는 최소 정보(`userId`, `placeId`, `visitId`, `text`)만 저장.
  2. 저장 이후 `ReviewResponseAssembler`가 `UserReadRepository`·`PlaceReadRepository`를 통해 사용자/장소 이름을 조회하여 DTO를 조립. 조회 실패 시 기본 문자열("알 수 없음")을 반환해 테스트 환경에서도 안전하게 동작.
  3. 목록 조회·수정 등 모든 경로에서 assembler를 사용하여 이름 정보를 포함한 응답을 반환.

## 2. Visit 연동 자격 검증 표준화
- **목적**: 리뷰/좋아요 작성 조건을 일관된 규칙으로 묶어 서비스 간 중복 로직을 제거하고 유지보수성을 확보.
- **주요 수정**
  - `VisitReadRepository`로 방문 존재 여부만 확인하는 읽기 전용 접근을 추가.
  - `VisitEligibilityChecker`가 방문 ID·사용자 ID를 기준으로 `ARRIVED/NOT_ARRIVED/NOT_FOUND` 상태를 판정.
  - `EligibilityCheckResponseDTO`에 `visitExists` 플래그를 추가해 예외 매핑을 세밀화.
- **로직 흐름**
  1. `ReviewService`, `DailyLikeService`는 공통으로 `visitEligibilityChecker.check(userId, visitId)` 호출.
  2. 결과가 `ARRIVED`가 아니면 DTO에 실패 사유와 상태 플래그를 담아 반환.
  3. 서비스는 DTO 상태에 따라 `REVIEW_NOT_ALLOWED`, `ARRIVAL_NOT_CONFIRMED`, `REVIEW_ALREADY_EXISTS`, `LIKE_ALREADY_EXISTS` 등 적절한 `FeedException`을 던짐.

## 3. 좋아요 플로우 정리
- **목적**: 리뷰와 동일한 철학으로 좋아요 도메인을 단순화하고 핵심 기능(등록/취소/재활성화)에 집중.
- **주요 수정**
  - `DailyLike`, `LikeStatus`, DTO/Repository/Service를 새로 작성해 상태(`ACTIVE/CANCELLED`)와 날짜(`dateKst`)만 유지.
  - `DailyLikeRepository`는 방문당 중복 여부 확인과 사용자/장소별 활성 좋아요 조회만 제공.
- **로직 흐름**
  - 등록: 자격 확인 → `DailyLike` 저장 → `DailyLikeResponseDTO` 반환.
  - 취소: 작성자 확인 → `dailyLike.cancel(now)` → 상태 `CANCELLED`.
  - 재활성화: 동일 사용자 확인 → `dailyLike.reactivate()` → 상태 `ACTIVE`.
  - 조회: 사용자·장소별 `ACTIVE` 좋아요만 반환.

## 4. JWT 사용자 정보 적용
- **목적**: 서비스 계층에서 JWT 기반 사용자 식별을 활용할 수 있도록 공통 사용자 디테일 정의.
- **주요 수정**
  - `CustomUserDetails`를 추가해 `userId`, `email`, `name`을 보관하고 권한을 `ROLE_USER`로 고정.

## 5. 테스트 & 인프라 보완 (세부 검증 항목)
- **목적**: 변경된 로직을 검증하고 Testcontainers가 없는 환경에서도 테스트를 수행할 수 있도록 조정.
- **단위 테스트**
  - `ReviewServiceTest`
    - `checkReviewEligibilityReturnsEligibleWhenArrived`: 방문 상태가 `ARRIVED`이고 중복 리뷰가 없을 때 DTO가 `eligible=true`로 반환되는지 검증.
    - `checkReviewEligibilityReturnsNotEligibleWhenVisitMissing`: 방문이 존재하지 않을 때 `visitExists=false`, `eligible=false`가 되는지 확인.
    - `checkReviewEligibilityReturnsNotEligibleWhenAlreadyWritten`: 동일 방문에 리뷰가 이미 존재하면 `alreadyWritten=true`로 작성이 차단되는지 검증.
  - `DailyLikeServiceTest`
    - `checkLikeEligibilityReturnsEligibleWhenArrived`: 방문이 도착 상태면서 중복 좋아요가 없을 때 `eligible=true`.
    - `checkLikeEligibilityReturnsNotEligibleWhenNotArrived`: 아직 도착하지 않은 방문이면 `visitArrived=false`와 함께 좋아요가 제한되는지 확인.
- **통합 테스트**
  - `ReviewServiceIntegrationTest`
    - `createReviewPersistsWhenEligible`: 방문 자격을 통과하면 리뷰가 저장되고 응답 DTO가 `reviewId`, `text`, 기본 이름(`알 수 없음`)을 포함하는지 확인.
    - `createReviewFailsWhenVisitMissing`: 방문이 없을 때 `FeedException(REVIEW_NOT_ALLOWED)`가 발생하는지 검증.
  - `DailyLikeServiceIntegrationTest`
    - `createLikePersistsWhenEligible`: 좋아요 등록 시 엔티티가 `ACTIVE` 상태로 저장되는지 확인.
    - `cancelLikeSetsStatusCancelled`: 취소 후 상태가 `CANCELLED`로 바뀌고 취소 시각이 기록되는지 검증.
    - `reactivateLikeSetsStatusActive`: 취소한 좋아요를 재활성화하면 상태가 `ACTIVE`로 복원되는지 확인.
    - `createLikeFailsWhenVisitMissing`: 방문이 없을 때 `FeedException(LIKE_NOT_ALLOWED)`가 발생하는지 검증.
    - `getUserPlaceLikesReturnsActiveOnes`: 사용자/장소별 조회 시 `ACTIVE` 상태 좋아요만 반환되는지 확인.
- **테스트 환경 구성**
  - `MatjomApplicationTests`는 Docker 미사용 환경에서 빌드 실패를 막기 위해 `@Disabled("Requires Docker to run Testcontainers")` 처리.
  - `JpaConfig`에 `DateTimeProvider`를 등록하고 `hibernate.jdbc.time_zone=UTC` 설정으로 감사 필드(`OffsetDateTime`)와 H2 테스트 간 시간대 차이를 해소.

## 6. 작업 상태 체크리스트 반영
- `tasks/prd-review-moderation.md`에 각 하위 작업(리뷰/좋아요 단순화, 방문 검증, 테스트 강화 등) 진행 결과와 관련 파일을 지속적으로 업데이트하여 진척도 추적.

---
위 변경으로 리뷰/좋아요 도메인이 JWT 사용자 흐름과 맞물리도록 정리되었고, 각 단위/통합 테스트가 핵심 기능(리뷰 작성, 좋아요 토글)이 바르게 동작함을 구체적으로 검증합니다.
