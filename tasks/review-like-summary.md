# Review & Like 구현 변경 기록

## 1. 리뷰 도메인 슬림화
- **목적**: 팀장 지침에 따라 리뷰 상태와 필드를 최소화해 관리 복잡도를 줄이고 JWT 기반 사용자 정보 흐름에 집중.
- **주요 수정**
- `Review` 엔티티는 BaseEntity의 `deleted_at`만 사용하며 별도 상태값을 두지 않는다.
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
- **기능 개요**: 리뷰 신고는 최소 정보(사유/설명)만 저장하고 자동 제재는 하지 않습니다. 리뷰 본문 검증은 `ReviewService`에서 직접 `ProfanityFilter`로 처리하고, 신고는 신고 이력과 건수 집계에 집중합니다.
- **흐름**
  1. 신고자가 동일 리뷰를 다시 신고하려 하면 `ReviewReportRepository.existsByReviewIdAndReporterId`가 중복을 차단합니다.
  2. 신고 대상 리뷰가 삭제되지 않은 상태로 존재하는지 `ReviewRepository.existsByIdAndDeletedAtIsNull`로 확인한 뒤 `review_reports`에 이력을 한 건 추가합니다.
  3. 응답 DTO(`ReportReviewResponseDTO`)는 신고 ID, 사유/설명, 신고 시각, 누적 건수(`reportCount`)만 내려 사용자에게 “신고 접수 완료” 정도의 정보만 제공합니다.
- **엔티티·스키마 연계**
  - `ReviewReport` 엔티티는 `review_id`와 `reporter_id`를 각각 리뷰/사용자와 연결합니다. 스키마에서도 `reporter_id` → `users(id)` FK를 지정해 신고자가 항상 유효한 사용자로 연결되도록 보장합니다.
  - 신고 건수 집계는 `ReviewReportRepository.countByReviewId` 단일 메서드로 처리하며 별도 경고 필드가 없습니다.
- **테스트**
  - `ReviewModerationServiceTest`에서 중복 신고 차단과 신고 건수 집계를 검증합니다.
  - 금칙어 검증은 리뷰 작성/수정 경로에서만 수행되며, 신고 기능은 단위 테스트로 최소 동작을 확인합니다.

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
  - 전문가 의견: 캐시 없이 DB에서 조건 한 번 조회 후 TTL(5분) 캐시로 충분히 대응 가능. 실시간 스트리밍이나 지속 배경 작업이 필요하지 않으며, 통계 요청 시점에 즉시 계산하는 스냅샷 접근이 현재 요구와 맞음.
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
## 11. 다음 단계 가이드 (9월 29일 최종)
- **Redis 도입 준비 체크** (변경 없음)
  - `build.gradle` 의존성:
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
        ttl-seconds: 300  # 필요 시 운영 상황에 맞게 조정 가능
    ```
  - 로컬 Redis는 Docker 컨테이너(`redis:7-alpine`)를 사용하고, 배치 완료 시 `DEL place:stats:{placeId}`/`DEL place:visit-info:{placeId}`로 캐시 무효화.
- **통계 작업 현황 요약**
  - UC-Stat-01: `placeName` 기반 응답으로 정리 완료. 오류 응답 규격만 남은 TODO로 유지.
  - UC-Stat-02: 실시간 체류 인원 응답은 신뢰성 문제로 제거. 향후 평균 기반 지표를 도입할 수 있도록 배치 데이터 구조만 유지.
  - UC-Batch-01: 집계/스케줄러/예측 훅/문서화까지 완료. 운영 모니터링 지표 정의는 보완 예정.
  - 공통: `./gradlew test` 전체 통과(9월 29일)로 회귀 검증 완료. README 갱신은 추후 팀장 확인 후 진행.

## 13. Review/Like API 단순화 (10월 ??일)
- 사용자 전용 화면에서 장소별 리뷰/좋아요 이력이 필요 없으므로 `GET /reviews/my/place/{placeId}`와 `GET /likes/my/place/{placeId}` 엔드포인트를 제거했다.
- 이에 따라 `ReviewService#getUserPlaceReviews`, `DailyLikeService#getUserPlaceLikes` 및 관련 레포지토리/테스트 코드도 정리했다.
- 리뷰 삭제 여부는 `BaseEntity.deleted_at`만 활용하도록 `ReviewStatus` enum과 상태 컬럼을 제거했다.

## 12. UC-Stat-01 및 UC-Stat-02 진행 기록 (9월 30일 최종)
- **UC-Stat-01 정리**
  - DTO & 스냅샷: `PlaceStatsResponseDTO`가 `placeName`, 누적 방문/좋아요, 특정 시간대(11~12시·12~13시) 최근 14일 평균 도착 수만 응답에 노출하고, `generatedAt`/`cacheTtlSeconds`/`dataSource`는 `@JsonIgnore`로 숨겨 내부 모니터링용으로 유지.
  - 서비스 계층: `PlaceStatisticsService`가 `PlaceReadRepository.findNameById`로 존재 여부와 이름을 동시에 확인해 DTO를 빌드.
  - 캐시 연동: `PlaceStatsCacheService`가 `put(Long placeId, PlaceStatsResponseDTO)` 형태로 변경돼 캐시 키는 placeId, 응답은 placeName을 유지.
  - 시간대 지표: `PlaceStatisticsRepository`가 최근 14일(당일 포함) 11~12시·12~13시 도착 인원을 평균 내어 반올림한 값을 반환하도록 갱신.
  - 테스트: 서비스/쿼리/컨트롤러 단위 테스트를 모두 갱신해 placeName 응답과 캐시 키 변화, fallback 흐름을 검증.
  - 문서: `docs/uc-stat-01-api.md`, `docs/statistics-change-log.md`, `docs/statistics-presentation.md`에 placeName 응답 및 최신 흐름 반영.
- **UC-Stat-02 상태**
  - 실시간 체류 인원 API(`/visit-info`)는 제거되었습니다. 향후 필요하면 배치 기반 평균 지표로 재설계합니다.
  - 관련 서비스/캐시/컨트롤러/테스트/문서는 정리되었고, 문서에서는 폐기 상태로 명시했습니다.
- **UC-Batch-01 유지보수**
  - 기존 집계/스케줄/예측 훅 구조는 그대로 유지하되, 캐시 무효화 대상에 새로 단순화된 visit-info 응답이 포함됨을 확인.
- **남은 TODO**
  - UC-Stat-01: 오류 응답 규격 정리.
  - UC-Stat-02: 추후 패턴/예측 재도입 시 재플래닝.
  - UC-Batch-01: 모니터링 지표 정의 보완.

## 13. Place Detail Facade 도입 (9월 30일 신규)
- **목적**: 장소 상세 화면에서 통계와 최신 리뷰를 묶어 전달하는 전용 API를 제공해 프론트엔드 호출 수를 최소화.
- **핵심 구성**
  - `PlaceDetailResponseDTO`: 통계(`PlaceStatsResponseDTO`), 리뷰 리스트(`ReviewResponseDTO`), 전체 리뷰 수를 포함하는 응답 DTO.
  - `PlaceDetailFacadeService`: 통계 캐시(`PlaceStatisticsQueryService`)와 리뷰 서비스(`ReviewService`)를 주입받아 조합하며, 기본으로 최근 5개 리뷰만 반환하고 `reviewLimit` 파라미터로 조절 가능.
  - `PlaceDetailController`: `GET /api/places/{placeId}/detail` 엔드포인트. 관리자 API가 아닌 일반 사용자 진입점을 위한 조합 API로 설계.
- **테스트**
  - `PlaceDetailFacadeServiceTest`: 기본 제한(5개)과 전체 리뷰 수 계산을 검증.
  - `PlaceDetailControllerTest`: MockMvc 기반으로 응답 구조와 쿼리 파라미터 전달을 확인.
