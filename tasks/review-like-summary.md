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

## 7. 추가 질의 & 결정 사항
- **ReviewReport의 reporter_id 연결성**: `review_reports` 테이블은 `reporter_id`를 `users(id)`에 FK로 묶어 신고자가 사용자 테이블과 정확히 연결됩니다. 엔티티는 `UUID reporterId`만 들고 있지만, 서비스에서 `reporterId`에 현재 사용자 `userId`를 저장하므로 DB와 코드 모두 동일 사용자 ID를 참조합니다.
- **UUID 사용 배경**: 리뷰/좋아요는 `visit` 기반으로 작성/좋아요 기회가 주어집니다. 분산 환경에서 ID 충돌 없이 생성할 수 있고(동시성), 외부 시스템 연계 시 추적이 용이하며, 순차 ID 노출 위험을 줄이기 위해 `UUID`를 채택했습니다. 방문 수만큼 리뷰/좋아요 기회를 부여하려는 비즈니스 규칙과도 정합성이 높습니다.

## 8. 리뷰 신고(Moderation) 정리
- **기능 개요**: 신고는 리뷰에 대한 금칙어 검증·신고 이력 저장에 집중하며, 자동 제재(숨김/삭제) 로직은 제거. 리뷰 상태는 신고만으로 바뀌지 않고 신고 건수(`reportCount`)만 누적.
- **흐름**
  1. `ReviewModerationService.validateText`가 작성/수정 시 profanity 필터(`ProfanityFilter`)로 금칙어를 차단합니다.
  2. 신고 요청(`reportReview`)이 들어오면 `review_reports` 테이블에 신고 이력을 저장하고, `ReviewReportRepository.countByReviewId(reviewId)`로 누적 신고 건수를 계산합니다.
  3. 응답 DTO(`ReportReviewResponseDTO`)는 신고자 이름(`reporterName`)과 누적 신고 건수(`reportCount`)만 내려주어 리뷰 상태 변화 없이 이력만 보여 줍니다.
- **엔티티·스키마 연계**
  - `ReviewReport` 엔티티는 `review_id`와 `reporter_id`를 각각 리뷰/사용자와 연결합니다. 스키마에서도 `reporter_id` → `users(id)` FK를 지정해 신고자가 항상 유효한 사용자로 연결되도록 보장합니다.
  - 신고 건수 집계는 `ReviewReportRepository.countByReviewId` 단일 메서드로 처리하며 별도 경고 필드가 없습니다.
- **테스트**
  - `ReviewModerationServiceTest`에서 금칙어 감지, 중복 신고 차단, 신고 건수 누적(상태 유지)을 검증합니다.
  - 금칙어/신고 기능은 서비스 단위 테스트로 다루며, 이전 통합 테스트는 제거하여 H2 DDL 충돌도 제거되었습니다.

## 9. 통계 구현 준비 메모 (Role: 데이터 아키텍트)
- **Tree-of-Thought**
  - 실시간 지표 전문가: `visits`의 상태·타임스탬프만으로 출발/도착/현재 체류 카운트를 산출할 수 있음을 확인했습니다.
  - 집계·배치 전문가: `place_daily_stats`가 일자·시간대·피크 필드를 이미 갖추고 있어 UC-Stat-02와 UC-Batch-01 요구사항을 수용할 수 있다고 평가했습니다.
  - DBA: `reviews`, `daily_likes`, `review_reports`가 방문/사용자/장소 FK와 타임스탬프, 상태 제약을 모두 갖춰 통계·신고 집계에 무리가 없음을 검증했습니다.
- **사용 가능한 핵심 데이터**
  - `visits`: 상태(`ACTIVE/ARRIVED`), `started_at`, `arrived_at`, 위치 정보 등 실시간 지표 산출에 필요한 필드가 준비되어 있음 (`src/main/resources/schema-postgres.sql:52`).
  - `places`: `place_id`, `name` 등 장소 메타 정보를 제공하여 응답 가독성을 높이는 데 활용 가능 (`src/main/resources/schema-postgres.sql:19`).
  - `reviews`: 방문당 1회 제한, 상태(`ACTIVE/DELETED`), 작성 시각을 보유해 리뷰 건수 및 최신 활동 확인에 적합 (`src/main/resources/schema-postgres.sql:118`).
  - `daily_likes`: 방문별 1회 좋아요, `date_kst` 필드를 통해 일별/당일 분석이 가능 (`src/main/resources/schema-postgres.sql:146`).
  - `place_daily_stats`: 일자별 출발·도착·리뷰·좋아요 수와 시간대 JSON, 피크 시간 컬럼으로 예측과 집계를 위한 기반 마련 (`src/main/resources/schema-postgres.sql:173`).
  - `review_reports`: 신고자/리뷰 연결, 신고 사유, 생성 시각을 저장해 신고 건수 요약이 용이 (`src/main/resources/schema-postgres.sql:198`).
- **결론**
  - UC-Stat-01/02, UC-Batch-01에 필요한 실시간·일일 집계·신고 데이터는 모두 기존 스키마에 구비되어 있습니다.
  - 현재 스키마만으로도 조회·집계·캐시 로직을 구현해 목표 Use Case를 지원할 수 있으며 추가 스키마 변경은 필요하지 않습니다.

## 10. 통계/캐시 관련 Q&A 메모 (Role: 데이터 아키텍트)
- **체류 인원 정의**
  - Tree-of-Thought: 실시간 지표 전문가는 “체류 인원 = 현재 장소에 머무르는 방문자”로 해석했고, 데이터 아키텍트는 `visits` 상태/타임스탬프 조건만으로 계산 가능하다고 강조.
  - 계산 방식: `state = 'ARRIVED'`이면서 `deleted_at`이 없고, 만료(`expired_at`)·취소(`cancelled_at`) 시간이 아직 지나지 않은 방문을 1명으로 합산.
- **실시간 처리 부담 여부**
  - 전문가 의견: 캐시 없이 DB에서 조건 한 번 조회 후 TTL(60초) 캐시로 충분히 대응 가능. 실시간 스트리밍이나 지속 배경 작업이 필요하지 않으며, 통계 요청 시점에 즉시 계산하는 스냅샷 접근이 현재 요구와 맞음.
- **Redis 캐시 사용 설명**
  - Redis는 메모리 키-값 저장소로 빠른 조회를 제공. `Cache-aside` 패턴으로 캐시 조회 → 미스 시 DB 조회 후 `SETEX` 저장 → TTL 만료 시 자동 삭제.
  - 키 패턴: `place:stats:{placeId}`, `place:visit-info:{placeId}`. 값은 DTO를 JSON으로 직렬화해 저장, `timestamp`와 `source`(캐시 여부) 필드 포함.
  - 배치에서는 집계 후 관련 키를 `DEL`로 무효화하여 다음 요청이 신선한 데이터를 계산하도록 함. Redis 장애 시에는 DB 계산만으로 응답하도록 방어 코드 작성.
- **Spring + Docker 기반 Redis 사용 절차**
  - 인프라 전문가: Docker 설치 확인 → `docker pull redis:7-alpine` → `docker run -d --name redis-local -p 6379:6379 -v redis-data:/data redis:7-alpine`으로 컨테이너 실행.
  - DevOps 전문가: 포트 매핑, 볼륨(`-v`)로 데이터 보존, `docker ps`로 상태 확인, 재시작 시 `docker stop/start redis-local` 사용.
  - 애플리케이션 전문가: `spring-boot-starter-data-redis` 의존성 추가 후 `application.yml`에 `spring.redis.host=localhost`, `spring.redis.port=6379` 설정. `RedisTemplate`을 이용해 캐시 로직을 구현.
  - 추가 복잡한 설계는 필요 없고, 팀 전원이 Docker Compose나 명령어를 공유해 동일한 Redis 환경을 쉽게 재현 가능.

## 11. 다음 단계 가이드 (9월 26일 최종)
- **Redis 도입 준비 체크**
  - `build.gradle` 의존성 추가:
    ```gradle
    implementation "org.springframework.boot:spring-boot-starter-data-redis"
    ```
  - `application.yml` 샘플:
    ```yaml
    spring:
      redis:
        host: localhost
        port: 6379
    statistics:
      cache:
        ttl-seconds: 60  # 필요 시 30으로 단축 가능
    ```
  - 로컬 Redis는 Docker 컨테이너(`redis:7-alpine`)를 기본으로 사용하고, 배치 완료 시 `DEL place:stats:{placeId}` 방식으로 캐시 무효화.
- **통계 작업 TODO(Statistics Task Plan 기준)**
  - UC-Stat-01: 응답 필드·오류 플로우 정의, 서비스/컨트롤러 구현, DTO 설계, 테스트/문서화 항목이 미완료 상태.
  - UC-Stat-02: 대기 인원 계산 방식, 패턴 범위, 외부 연동 범위 확정 등 요구 정밀화와 서비스/테스트/문서화 전반이 TODO.
  - UC-Batch-01: 실제 집계 로직 구현(`visits/reviews/daily_likes` 연동), 배치 API, 시간대 통계/재학습, 테스트 및 운영 문서 작성이 남아 있음.
  - 공통: `./gradlew test` 전체 통과 확인과 README/summary 갱신도 최종 마무리 단계에서 수행 필요.
