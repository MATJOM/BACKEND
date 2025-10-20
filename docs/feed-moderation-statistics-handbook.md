# MatJom Core Domain Handbook  
**Feed · Moderation · Statistics**

> _작성일: 2025-09-30_  
> _대상 독자: MatJom 백엔드 신입 개발자, 코드 리뷰어, 운영 담당자_

본 핸드북은 MatJom 프로젝트의 세 축인 **Feed(리뷰·좋아요)**, **Moderation(신고·금칙어)**, **Statistics(장소 통계)** 패키지를 깊이 있게 이해하고 유지보수하기 위한 기술 문서입니다. 단순 API 명세를 넘어, 각 패키지가 해결하려는 문제, 연관 테이블/서비스, 내부에서 일어나는 주요 알고리즘, 그리고 서로 어떻게 상호 작용하는지까지 상세히 서술합니다.

문서는 크게 다음과 같은 질문에 답합니다.

1. 각 패키지는 어떤 문제를 해결하는가? (도메인 책임)
2. 요청 하나가 들어오면 컨트롤러 → 서비스 → 리포지토리 → DTO로 어떤 작업이 수행되는가? (로직 흐름)
3. 어떤 코드가 핵심을 담당하며, 그 코드가 왜 그런 방식으로 작성되었는가? (구현 의도와 근거)
4. 패키지 간 데이터는 어떻게 교차하며, 변경 시 어떤 영향이 있는가? (상호 의존성)
5. 프런트엔드에는 어떤 데이터를 어떤 형태로 돌려주며, 에러는 어떻게 처리되는가? (계약 및 예외)

---

## 1. 공통 토대와 시스템 가정

### 1.1 사용자 식별과 인증 흐름

- 모든 HTTP API는 JWT 기반 인증을 사용합니다. 스프링 시큐리티 필터가 토큰을 검증한 후 `CustomUserDetails` 객체를 생성하여 `SecurityContext`에 저장합니다.
- 컨트롤러는 `@RequestAttribute("userId")`로 인증된 사용자 UUID를 전달받습니다. 이 값은 서비스 계층에서 도메인 로직을 처리할 때 일관되게 사용됩니다.
- 이 구조 덕분에 서비스/리포지토리는 시큐리티 프레임워크에 의존하지 않고 순수한 UUID만 다루므로, 테스트 시에도 별도 시큐리티 설정 없이 Mock UUID를 주입할 수 있습니다.

### 1.2 BaseEntity와 소프트 삭제 정책

- `Review`, `DailyLike`, `ReviewReport` 등 핵심 테이블은 `BaseEntity`를 상속해 `createdAt`, `updatedAt`, `deletedAt` 필드를 공유합니다.
- 물리 삭제 대신 `deletedAt`을 채우는 방식으로 논리 삭제를 수행합니다. 엔티티에서는 `isDeleted()` 또는 `isActive()` 헬퍼를 두어 표현을 단순화합니다.
- 통계/신고에서 “현재 유효한 리뷰인지” 판별할 때는 `deletedAt IS NULL` 조건을 사용합니다. 코드에서도 이를 반영하기 위해 `ReviewRepository.existsByIdAndDeletedAtIsNull` 메서드를 제공합니다.

### 1.3 Clock 주입과 시간대 전략

- `src/main/java/com/matjom/matjom/common/config/TimeConfig.java`는 전역적으로 공유하는 `Clock.systemUTC()` 빈을 정의합니다.
- 통계/배치/모더레이션에서 모든 시간 계산은 이 Clock을 주입받아 처리합니다. 이를 통해 테스트에서 고정 시계를 주입하거나, 다중 인스턴스 환경에서 동일한 기준 시각을 사용하도록 보장합니다.
- 통계에서 KST(Asia/Seoul)를 기준으로 일자를 계산해야 할 경우 `OffsetDateTime.now(clock).atZoneSameInstant(KST)` 형태로 변환합니다.

---

## 2. Feed 패키지 – 리뷰 & 좋아요 도메인

### 2.1 도메인 개요

Feed 패키지는 사용자가 음식점 방문 기록(`visit`)을 바탕으로 남기는 **리뷰**와 **좋아요** 기능을 담당합니다. 주요 목표는 다음과 같습니다.

1. _방문 당 1회_라는 제약 하에서 리뷰/좋아요를 생성·수정·삭제(혹은 재활성화) 할 수 있도록 한다.
2. 최소한의 검증만 수행해 도메인을 단순하게 유지하되, 핵심 UX를 보호한다 (방문 여부 확인, 금칙어 차단 등).
3. 응답에서 사용자 이름과 장소 이름을 제공해 프런트가 별도 요청 없이도 정보 제공을 완료할 수 있게 한다.

### 2.2 주요 구성요소 맵

| 레이어 | 클래스 | 역할 |
| ------ | ------ | ---- |
| Controller | `ReviewController`, `DailyLikeController` | HTTP 요청 바인딩, 사용자 UUID 추출, 서비스 호출 |
| Service | `ReviewService`, `DailyLikeService` | 비즈니스 로직, 검증, 엔티티 조립 |
| Helper | `VisitEligibilityChecker` | 리뷰/좋아요 공통 방침(ARRIVED 여부) 재사용 |
| Repository | `ReviewRepository`, `DailyLikeRepository`, `VisitReadRepository`, `UserReadRepository`, `PlaceReadRepository` | 데이터 접근 |
| DTO | `ReviewResponseDTO`, `DailyLikeResponseDTO`, `ReviewCreateRequestDTO`, `DailyLikeCreateRequestDTO` 외 | 컨트롤러 ↔ 서비스 ↔ 프런트 데이터 계약 |

### 2.3 방문 자격 검증 – “ARRIVED” 여부만 본다

리뷰와 좋아요는 모두 방문이 실제 완료되었을 때만 허용됩니다. 이를 위해 두 서비스는 공통 헬퍼 `VisitEligibilityChecker`를 의존합니다.

```java
// src/main/java/com/matjom/matjom/feed/service/VisitEligibilityChecker.java
@Component
@RequiredArgsConstructor
public class VisitEligibilityChecker {

    private final VisitReadRepository visitReadRepository;

    /**
     * 특정 사용자(userId)가 특정 방문(visitId)에 대해 ARRIVED 상태인지 확인한다.
     * 엔티티 전체를 불러오지 않고 Boolean 쿼리만 수행해 성능을 최적화한다.
     */
    public boolean isArrived(UUID userId, Long visitId) {
        return visitReadRepository.existsByIdAndUserIdAndState(visitId, userId, VisitState.ARRIVED);
    }
}
```

리포지토리 구현은 아래와 같이 `SELECT CASE WHEN COUNT > 0` 쿼리를 사용합니다. 이렇게 하면 JPA가 `Visit` 엔티티의 다른 필드(좌표, 메타 JSON 등)를 전혀 로딩하지 않아도 되므로 최소 비용으로 자격을 판정할 수 있습니다.

```java
// src/main/java/com/matjom/matjom/feed/repository/VisitReadRepository.java
@Query("""
        SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
        FROM Visit v
        WHERE v.id = :visitId
          AND v.user.id = :userId
          AND v.state = :state
          AND v.deletedAt IS NULL
    """)
boolean existsByIdAndUserIdAndState(@Param("visitId") Long visitId,
                                    @Param("userId") UUID userId,
                                    @Param("state") VisitState state);
```

### 2.4 리뷰 라이프사이클 상세

#### 2.4.1 요청 흐름

1. `POST /api/v1/reviews`
2. `ReviewController`가 `ReviewCreateRequestDTO(placeId, visitId, text)`를 바인딩하고 `ReviewService.createReview`를 호출합니다.
3. `ReviewService`는 다음 순서로 검증 및 저장을 수행합니다.
   - (1) ARRIVED 여부 확인
   - (2) 동일 방문에 이미 리뷰가 있는지 검사 (`ReviewRepository.existsByVisitId`)
   - (3) 금칙어 검증 (`profanityFilter.validate`)
   - (4) 엔티티 생성 후 저장, 응답 DTO 조립

아래 코드는 실제 저장 로직의 핵심 부분입니다.

```java
// src/main/java/com/matjom/matjom/feed/service/ReviewService.java
Review savedReview = reviewRepository.save(Review.builder()
        .userId(userId)
        .placeId(request.getPlaceId())
        .visitId(request.getVisitId())
        .text(request.getText())
        .build());

return reviewResponseAssembler.toDto(savedReview);
```

#### 2.4.2 응답 조립 – 이름 조회와 오류 내성

리뷰 응답은 `ReviewResponseDTO`를 사용하며, 사용자명/장소명을 모두 포함합니다. 어셈블러는 DB 조회 실패 시에도 기본 문자열 "알 수 없음"을 반환해 API 전체가 실패하지 않도록 구성되어 있습니다.

```java
// src/main/java/com/matjom/matjom/feed/dto/assembler/ReviewResponseAssembler.java
private String loadReviewerName(UUID userId) {
    try {
        return userReadRepository.findNameById(userId).orElse(UNKNOWN);
    } catch (DataAccessException ex) {
        log.debug("사용자 이름 조회 실패: userId={}", userId, ex);
        return UNKNOWN;
    }
}
```

> **주의:** 사용자/장소 이름 조회는 읽기 전용 보조 쿼리입니다. 서비스 장애를 막기 위해 예외를 잡아 삼키고 기본값을 제공하지만, 로그에는 디버깅용 정보가 남습니다.

### 2.5 좋아요 라이프사이클 상세

좋아요 흐름은 리뷰와 거의 동일하되, 상태가 `ACTIVE ↔ CANCELLED` 로 전환된다는 점이 다릅니다.

- `createLike`: 방문 자격·중복 검사 후 엔티티 저장
- `cancelLike`: 해당 사용자의 `likeId`를 찾아 상태를 `CANCELLED`로 전환, 취소 시각 기록
- `reactivateLike`: `CANCELLED` 상태인지 확인 후 `ACTIVE`로 복구

```java
// src/main/java/com/matjom/matjom/feed/entity/likes/DailyLike.java
public void cancel(OffsetDateTime cancelledAt) {
    this.status = LikeStatus.CANCELLED;
    this.cancelledAt = cancelledAt;
}

public void reactivate() {
    this.status = LikeStatus.ACTIVE;
    this.cancelledAt = null;
}
```

### 2.6 오류 처리와 예외 코드

Feed 도메인은 `FeedException`을 사용하며, `ErrorCode` Enum에 사유를 정의합니다.

| 상황 | ErrorCode | HTTP 기본값 |
| ---- | --------- | ------------ |
| ARRIVED 전 작성 | `REVIEW_NOT_ALLOWED` / `LIKE_NOT_ALLOWED` | 400 |
| 중복 작성/좋아요 | `REVIEW_ALREADY_EXISTS` / `LIKE_ALREADY_EXISTS` | 400 |
| 타인 리뷰 수정·삭제 | `FORBIDDEN` | 403 |
| 대상 없음 | `REVIEW_NOT_FOUND` 등 | 404 |

컨트롤러 레이어는 `ApiResponse.ok()` 또는 예외를 던지는 방식으로만 응답하므로, 공통 에러 처리기가 HTTP 상태와 메시지를 생성합니다.

### 2.7 API 계약 일람 (Feed)

| 엔드포인트 | 요청 DTO | 주요 요청 필드 | 응답 DTO | 주요 응답 필드 |
| ----------- | --------- | -------------- | -------- | ---------------- |
| `POST /api/v1/reviews` | `ReviewCreateRequestDTO` | `placeId`, `visitId`, `text` | `ReviewResponseDTO` | `reviewId`, `reviewerName`, `placeName`, `text`, `createdAt` |
| `PUT /api/v1/reviews/{reviewId}` | `ReviewUpdateRequestDTO` | `text` | `ReviewResponseDTO` | `reviewId`, `reviewerName`, `placeName`, `text`, `createdAt` |
| `DELETE /api/v1/reviews/{reviewId}` | - | 경로 변수 `reviewId` | `ApiResponse<Void>` | 성공 여부만 반환 |
| `GET /api/v1/reviews/my` | - | JWT 사용자 | `List<ReviewResponseDTO>` | 사용자 리뷰 목록 |
| `GET /api/v1/reviews?placeId=` | - | `placeId` 쿼리 파라미터 | `List<ReviewResponseDTO>` | 장소 리뷰 목록 |
| `POST /api/v1/likes` | `DailyLikeCreateRequestDTO` | `placeId`, `visitId` | `DailyLikeResponseDTO` | `likeId`, `userName`, `placeName`, `visitId`, `status`, `createdAt` |
| `DELETE /api/v1/likes/{likeId}` | - | 경로 변수 `likeId` | `ApiResponse<Void>` | 성공 여부만 반환 |
| `PUT /api/v1/likes/{likeId}` | - | 경로 변수 `likeId` | `DailyLikeResponseDTO` | `likeId`, `userName`, `placeName`, `status`, `createdAt` |

> **메모:** `ReviewResponseDTO`와 `DailyLikeResponseDTO`는 이름 기반 표시를 위해 사용자/장소명을 포함하며, ID 대신 이름만 프런트에 노출해야 할 경우 그대로 사용하면 됩니다.

### 2.8 테스트 전략

- `ReviewServiceTest`, `DailyLikeServiceTest`: Mockito 기반 단위 테스트. 자격 미충족/중복/정상 케이스를 커버합니다.
- `ReviewServiceIntegrationTest`, `DailyLikeServiceIntegrationTest`: `@SpringBootTest`로 실제 리포지토리를 띄워 CRUD가 정상 동작하는지 확인합니다. 방문 자격은 `@MockBean`으로 대체하여 도메인 핵심에 집중합니다.

테스트 메소드에는 "무엇을 테스트하는지, 어떤 상황인지, 기대 결과"를 한글 주석으로 명시해 향후 유지보수자가 쉽게 이해할 수 있습니다 (예: `src/test/java/com/matjom/matjom/feed/service/ReviewServiceIntegrationTest.java:43`).

---

## 3. Moderation 패키지 – 신고와 금칙어

### 3.1 도메인 개요

Moderation 패키지는 두 가지 책임을 가집니다.

1. 리뷰 작성/수정 시 금칙어를 탐지해 저장을 차단한다.
2. 사용자가 리뷰를 신고할 수 있게 하고, 신고 이력과 누적 건수를 저장한다.

자동 제재(리뷰 삭제, 숨김) 등은 싱글 책임 원칙에 따라 포함하지 않았습니다. 신고 건수는 통계/운영 툴에서 활용할 수 있도록 저장만 합니다.

### 3.2 금칙어 필터

`ProfanityFilter` 인터페이스는 `validate(String text)` 한 메서드만 제공합니다. 구현체인 `HardcodedProfanityFilter`는 간단한 블랙리스트를 사용합니다.

```java
// src/main/java/com/matjom/matjom/moderation/profanity/HardcodedProfanityFilter.java
private final Set<String> blacklist = Set.of("욕설1", "욕설2", "비속어3");

private boolean hasBlacklistedWord(String text) {
    if (text == null || text.isBlank()) {
        return false;
    }
    String normalized = text.toLowerCase().replaceAll("[^가-힣a-z0-9]", "");
    return blacklist.stream().anyMatch(normalized::contains);
}
```

- 리뷰 작성 시 `ReviewService`가 `profanityFilter.validate(request.getText())`를 호출합니다.
- 금칙어가 발견되면 `FeedException(ErrorCode.REVIEW_BAD_LANGUAGE)`가 발생하고 API는 400으로 응답합니다.
- 향후 확장(외부 필터 연동 등)을 고려해 인터페이스 기반으로 설계되었습니다.

### 3.3 리뷰 신고 세부 흐름

1. **중복 신고 차단**: `ReviewReportRepository.existsByReviewIdAndReporterId`로 동일 사용자의 중복 신고를 막습니다.
2. **리뷰 존재 확인**: `ReviewRepository.existsByIdAndDeletedAtIsNull`을 호출해 삭제된 리뷰가 아닌지 확인합니다.
3. **신고 저장**: `ReviewReport` 엔티티를 생성해 신고 사유/설명을 저장합니다.
4. **누적 3회 이상이면 자동 삭제**: `ReviewRepository.findById`로 리뷰를 불러와 `markDeleted()` 처리합니다.
5. **응답**: 성공 여부만 돌려주고, 프런트는 “신고가 완료되었습니다. 신고 내용은 관리자에게 전달되었고, 확인 후 조치 예정입니다.” 같은 고정 문구를 자체적으로 보여주면 됩니다.

```java
// src/main/java/com/matjom/matjom/moderation/report/service/ReviewModerationService.java
ReviewReport saved = reviewReportRepository.save(...);
long reportCount = reviewReportRepository.countByReviewId(reviewId);

if (reportCount >= 3) {
    reviewRepository.findById(reviewId)
            .filter(review -> !review.isDeleted())
            .ifPresent(review -> {
                review.markDeleted();
                log.info("리뷰 자동 삭제 처리: reviewId={}, reportCount={}", reviewId, reportCount);
            });
}

log.info("리뷰 신고 기록 생성: reviewId={}, reporterId={}, reportId={}, reportCount={}",
        reviewId, reporterId, saved.getId(), reportCount);

// 컨트롤러에서는 ApiResponse.ok()만 반환해 성공 여부만 전달한다.
```

```tsx
// 예시: 프론트(React)에서 Axios와 토스트 컴포넌트를 사용해 신고 완료 메시지 출력
import axios from 'axios';
import { toast } from '@/components/ui/toast';

async function handleReport(reviewId: string, payload: { reason: string; description?: string }) {
  try {
    const response = await axios.post<ApiResponse<void>>(
      `/api/v1/reviews/${reviewId}/reports`,
      payload,
      { headers: { Authorization: `Bearer ${token}` } }
    );

    if (response.data.success) {
      toast.success('신고가 완료되었습니다. 신고 내용은 관리자에게 전달되었고, 확인 후 조치 예정입니다.');
    } else {
      toast.error(response.data.error?.message ?? '신고 처리 중 문제가 발생했습니다.');
    }
  } catch (error) {
    toast.error('네트워크 오류로 신고에 실패했습니다.');
  }
}
```

### 3.4 데이터 스키마와 엔티티

- `review_reports` 테이블은 `review_id`, `reporter_id`, `reason`, `description`을 갖습니다. `reporter_id`는 `users(id)`를 참조합니다.
- 엔티티(`ReviewReport`) 역시 UUID 기반으로 FK를 저장하므로, 신고와 사용자/리뷰 간 연결이 명확합니다.

### 3.5 API 계약 (프런트 가이드)

- 요청: `POST /api/v1/reviews/{reviewId}/reports`
- 요청 바디: `reason`(필수 ENUM), `description`(선택)
- 응답: 본문 없이 성공 여부만 반환 (`ApiResponse.ok()`)
- 프런트는 “신고가 완료되었습니다. 신고 내용은 관리자에게 전달되었고, 확인 후 조치 예정입니다.” 같은 문구를 자체적으로 띄운다.

### 3.6 테스트 전략

`ReviewModerationServiceTest`는 다음 세 가지 핵심 시나리오를 검증합니다.

1. 중복 신고 시 `REVIEW_REPORT_ALREADY_EXISTS`
2. 존재하지 않는 리뷰 신고 시 `REVIEW_NOT_FOUND`
3. 정상 신고 시 DB에 신고 이력이 저장되고, `ReviewService`와 `ReviewRepository`를 통해 누적 신고 수 3회 이상이면 자동으로 소프트 삭제된다.

### 3.7 Feed와의 상호 작용

- **금칙어 검증**: 리뷰 작성/수정 시 Moderation 패키지의 `ProfanityFilter`를 활용합니다.
- **리뷰 존재 여부 확인**: 신고 시 Feed의 `ReviewRepository`를 참조합니다.
- **신고 데이터 활용**: 현재는 통계 패키지에서 직접 사용하진 않지만, 향후 신고율 등의 메트릭을 위해 `ReviewReport` 데이터를 참조할 수 있습니다.

---

## 4. Statistics 패키지 – 장소 통계와 배치

### 4.1 목표와 범위

Statistics 패키지는 사용자에게 음식점의 전반적인 인기와 혼잡도를 판단할 수 있는 정보를 제공합니다. 주요 지표는 다음과 같습니다.

- `totalVisitors`: 누적 방문자 수
- `totalLikes`: 누적 좋아요 수
- `arrivals11To12`, `arrivals12To13`: 최근 14일 동일 시간대 평균 방문자 수 (11~12시, 12~13시)

또한 캐시/배치/예측 구조를 갖추고 있어 향후 실시간 추천, 혼잡도 예측으로 확장할 수 있는 기반을 제공합니다.

### 4.2 구성요소 지도

| 레이어 | 클래스 | 설명 |
| ------ | ------ | ---- |
| Controller | `PlaceStatisticsController`, `DailyStatsBatchController` | 통계 조회, 배치 트리거 |
| Service | `PlaceStatisticsQueryService`, `PlaceStatisticsService`, `DailyStatsBatchService`, `DailyStatsBatchScheduler`, `DailyStatsPredictionService` | 캐시 전략, 실시간 조회, 배치 집계, 예측 연동 |
| Cache | `PlaceStatsCacheService` | Redis 캐시 관리 |
| Repository | `PlaceStatisticsRepository`, `PlaceDailyStatsReadRepository`, `PlaceDailyStatsBatchRepository` | 통계 스냅샷/집계 쿼리 |
| DTO | `PlaceStatsResponseDTO`, `PlaceStatsSnapshot`, `StatsDataSource` | 응답 구조 |

### 4.3 실시간 통계 조회 시퀀스

1. **컨트롤러 호출**: `GET /api/places/{placeId}/stats`
2. **캐시 조회**: `PlaceStatsCacheService.get(placeId)` – Redis에 저장된 JSON이 있다면 즉시 반환
3. **캐시 미스**: `PlaceStatisticsService.fetchPlaceStats(placeId)` 호출
   - (a) `PlaceReadRepository.findNameById(placeId)`로 장소 존재 여부 및 이름 확보
   - (b) `PlaceStatisticsRepository.fetchSnapshot(placeId, 기준일)`로 DB 스냅샷 조회
   - (c) `PlaceStatsResponseDTO.of(...)`로 DTO 작성
4. **캐시 저장**: 새 DTO를 캐시에 저장 (`cache.ttl-seconds` 설정값 사용)
5. **응답 반환**: 최종 DTO를 프런트에 전달. 내부 필드(`generatedAt`, `cacheTtlSeconds`, `dataSource`)는 `@JsonIgnore`

```java
// src/main/java/com/matjom/matjom/statistics/cache/PlaceStatsCacheService.java
public void put(Long placeId, PlaceStatsResponseDTO response) {
    long ttlSeconds = Math.max(response.getCacheTtlSeconds(), 1L);
    PlaceStatsResponseDTO payload = response.withDataSource(StatsDataSource.CACHE);
    String serialized = objectMapper.writeValueAsString(payload);
    redisTemplate.opsForValue().set(key(placeId), serialized, Duration.ofSeconds(ttlSeconds));
}
```

### 4.4 PlaceStatsSnapshot과 DB 쿼리

- `PlaceStatisticsRepository.fetchSnapshot`은 방문(`visits`), 리뷰(`reviews`), 좋아요(`daily_likes`) 테이블에서 누적치를 계산하고, 과거 14일의 `place_daily_stats`에서 평균 시간대 데이터를 가져옵니다.
- 반환 타입 `PlaceStatsSnapshot`은 Storage 로직을 서비스에 노출하지 않고 DTO 변환만 담당합니다.

```java
// src/main/java/com/matjom/matjom/statistics/dto/PlaceStatsSnapshot.java
public record PlaceStatsSnapshot(
        long totalVisitors,
        long totalLikes,
        long arrivals11To12,
        long arrivals12To13
) {}
```

### 4.5 배치와 예측

- `DailyStatsBatchScheduler`는 `@Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")`로 매일 00시 KST에 실행됩니다.
- 배치 서비스는 다음 작업을 수행합니다.
  1. 전일 방문/리뷰/좋아요 수를 `place_daily_stats`에 집계
  2. Redis 캐시를 무효화 (`PlaceStatsCacheService.evict`)하여 다음 조회 시 최신 데이터를 계산하도록 유도
  3. `DailyStatsPredictionService`에 통지해 추후 예측 모델을 호출할 수 있는 훅을 제공합니다 (현재는 TODO 상태)

### 4.6 통계 응답 계약

```json
{
  "placeName": "맛좋은 식당",
  "totalVisitors": 1200,
  "totalLikes": 450,
  "arrivals11To12": 32,
  "arrivals12To13": 45
}
```

- 프런트는 응답 필드를 그대로 카드/그래프 등에 렌더링하면 됩니다.
- `generatedAt`, `cacheTtlSeconds`, `dataSource`는 내부적으로만 사용되며 `@JsonIgnore` 처리되어 외부로 노출되지 않습니다.

### 4.7 API 계약 일람 (Statistics)

| 엔드포인트 | 요청 DTO | 주요 요청 필드 | 응답 DTO | 주요 응답 필드 |
| ----------- | --------- | -------------- | -------- | ---------------- |
| `GET /api/places/{placeId}/stats` | - | 경로 변수 `placeId` | `PlaceStatsResponseDTO` | `placeName`, `totalVisitors`, `totalLikes`, `arrivals11To12`, `arrivals12To13` |
| `POST /api/batch/midnight-reset` (내부) | - | 본문 없음 | `ApiResponse<Void>` | 배치 실행 결과 |
| `GET /api/batch/status/last` (내부) | - | 본문 없음 | `DailyStatsBatchStatusResponse` (TODO) | 최근 배치 시각 등 (향후 확장) |

`PlaceStatsResponseDTO` 내부 필드 해석:

- `placeName`: 장소명 (프런트 표시용)
- `totalVisitors`: 누적 방문자 수
- `totalLikes`: 누적 좋아요 수
- `arrivals11To12`, `arrivals12To13`: 최근 14일 동일 시간대 평균 도착 수
- `generatedAt`, `cacheTtlSeconds`, `dataSource`: JSON 응답에서 숨김 (`@JsonIgnore`), 관측/모니터링용

### 4.8 에러 처리

- 존재하지 않는 장소 요청 시 `PlaceException(ErrorCode.PLACE_NOT_FOUND)`가 발생하며 404로 응답됩니다.
- Redis 캐시 에러(`JsonProcessingException` 등)는 경고 로그만 남기고 캐시 항목을 삭제한 뒤 DB 조회 결과를 반환합니다. 즉, 캐시 장애가 사용자 경험에 영향을 주지 않도록 설계되어 있습니다.

### 4.9 테스트 전략

- `PlaceStatisticsServiceTest`: Clock 주입, 장소 미존재 예외, 스냅샷 변환을 단위 테스트합니다.
- `PlaceStatisticsControllerTest`: Spring MVC 슬라이스 테스트로 캐시 히트/미스 시 컨트롤러 응답을 검증합니다.
- `DailyStatsBatchServiceTest`: 배치가 호출할 때 캐시 무효화/예측 트리거가 동작하는지 테스트합니다.

---

## 5. 패키지 간 상호작용과 데이터 플로우

### 5.1 의존 관계 개요

```
Feed ──────▶ Moderation (금칙어 검증)
  │            ▲
  │            │
  ▼            └─ 신고 시 리뷰 존재 여부 확인
Statistics ──▶ Feed (장소명 조회, 리뷰/좋아요 집계)

```

- **Feed ↔ Moderation**: Feed는 `ProfanityFilter`를 사용해 텍스트를 검증하며, Moderation은 신고를 저장하기 전에 Feed의 `ReviewRepository`로 리뷰 존재를 확인합니다.
- **Feed ↔ Statistics**: 통계 조회 시 `PlaceReadRepository` 등 Feed의 리포지토리를 활용합니다. 반대로 통계 결과는 Feed 응답에는 직접 포함되지 않지만, 같은 장소 ID를 사용하므로 프런트에서 리뷰/통계를 함께 요청하면 UI에 시너지가 생깁니다.

### 5.2 시퀀스 예제 – “리뷰 작성 후 통계 조회”

1. **리뷰 작성** (`POST /reviews`)
   - Feed가 ARRIVED 여부, 금칙어, 중복 검사를 수행하고 리뷰 저장
   - 이벤트 발행 대신 통계는 "조회 시점 계산" 방법을 채택 (실시간 반영은 통계 쪽에서 DB 조회로 수행)
2. **통계 조회** (`GET /places/{id}/stats`)
   - 캐시가 있다면 즉시 반환, 없다면 통계 레포지토리에서 최신 데이터를 집계
   - 리뷰 수는 `reviews` 테이블에서 직접 계산되므로 방금 작성한 리뷰가 바로 반영됩니다.

### 5.3 시퀀스 예제 – “리뷰 신고 흐름”

1. 프런트가 특정 리뷰를 신고 (`POST /reviews/{reviewId}/reports`)
2. Moderation 서비스가 중복 신고 여부, 리뷰 존재 여부를 검사
3. 신고 이력을 저장하고 누적 신고 건수를 응답으로 반환
4. 통계/운영 시스템은 `review_reports` 테이블을 통해 신고 건수를 별도 분석할 수 있습니다.

---

## 6. 테스트와 품질 보증

- **단위 테스트**: 각 서비스는 핵심 분기(허용/거부)를 모두 커버하도록 작성했습니다. Mockito를 사용해 의존성(리포지토리, 필터 등)을 주입하고, 코드 상단에 목적/상황/기대를 주석으로 명시했습니다.
- **통합 테스트**: Feed 도메인은 실제 JPA 리포지토리를 사용해 CRUD를 검증합니다. Visit 자격은 MockBean으로 처리해 테스트 환경에서 Visit 데이터를 별도로 준비하지 않아도 됩니다.
- **통계 테스트**: 캐시 미스/히트, 장소 미존재, 배치 작동 여부를 확인합니다. Redis 의존성을 제거하기 위해 `StringRedisTemplate` 대신 내장형 대체(Mock)를 사용하거나 테스트 프로파일에서 메모리 템플릿으로 오버라이드하는 전략을 사용할 수 있습니다.

---

## 7. 운영 및 향후 확장 가이드

1. **금칙어 관리**: 현재는 하드코딩된 블랙리스트를 사용합니다. 외부 서비스 연동이나 DB 기반 관리로 확장하려면 `ProfanityFilter` 구현체만 교체하면 됩니다.
2. **신고 후 조치**: 누적 신고 건수가 일정 기준을 넘으면 운영자가 후속 조치를 취하도록 알림 시스템과 연계할 수 있습니다. API 응답의 `reportCount`는 사용자(신고자)에게도 투명성을 제공합니다.
3. **통계 확장**: `PlaceStatsSnapshot`에 새 필드를 추가하면 DTO/캐시/프런트까지 확장해야 합니다. 변경 시 본 핸드북과 `docs/uc-stat-01-api.md`, `statistics-task-plan.md`를 함께 업데이트하세요.
4. **캐시 TTL**: 현재 기본 TTL은 `statistics.cache.ttl-seconds`(기본 300초)입니다. 자정 배치가 실행되면 `PlaceStatsCacheService.evict`가 호출되어 새 데이터를 강제로 계산하도록 합니다. 실시간성이 더 중요해지면 TTL을 줄이고 캐시 미스 빈도가 높아지는지 모니터링해야 합니다.

---

## 8. 추가 참고 문서

- `docs/review-report-api.md` – 리뷰 신고 API 상세 명세
- `docs/statistics-presentation.md` – 통계 아키텍처 슬라이드 요약
- `docs/statistics-change-log.md` – 통계 패키지 변경 이력
- `tasks/review-like-summary.md` – Feed/Moderation/Statistics 작업 로그 및 Q&A

---

## 9. 마무리

이 문서가 목표로 하는 바는 "코드를 직접 열어 확인하지 않아도 핵심 설계 의도를 이해"할 수 있도록 돕는 것입니다. 변경 사항이 생기면 반드시 해당 섹션을 갱신해 팀 전체가 최신 상태를 공유하세요. 특히 응답 DTO 필드나 예외 정책이 달라질 경우 프런트/QA/운영 문서까지 연쇄적으로 영향을 받습니다.

> **Checklist – 수정 시 같이 업데이트할 것**
> 1. 본 핸드북 (패키지 개요, 코드 스니펫)
> 2. 관련 API 문서 (`docs/review-report-api.md`, `docs/uc-stat-01-api.md` 등)
> 3. 작업 로그 (`tasks/review-like-summary.md`, `tasks/statistics-task-plan.md`)
> 4. 테스트 (단위·통합)와 기대 결과 주석

---

_이상으로 Feed · Moderation · Statistics의 현재 구조를 마스터했습니다. 신규 기능을 설계하거나 이슈를 디버깅할 때 본 문서를 시작점으로 삼으세요._


. 전체 화면 구조(레이아웃)
히어로 영역

장소 대표 이미지(16:9), 장소명, 카테고리, 주소, 영업시간 등을 카드 형태로 노출
히어로 하단에 placeId와 현재 ARRIVED 상태의 visitId를 내부 상태로 보관
예: 길찾기/도착 프로세스에서 이미 visitId를 전달받았다면 PlaceDetailPage 컴포넌트 props나 global state로 전달
액션 버튼 영역

히어로 바로 아래에 두 개의 버튼만 배치
리뷰 쓰기 (📝 아이콘)
→ 클릭 시 “리뷰 작성 페이지” 또는 모달로 이동
좋아요 (👍 아이콘)
→ 클릭 시 바로 좋아요 API 호출
두 버튼 모두 현재 페이지에서 visitId를 알고 있다고 가정(ARRIVED 상태)
리뷰/테스트 섹션

통계 KPIs
리뷰 목록(작성자 이름, 날짜, 본문 + 좋아요 상태)
신고 버튼 (필요 시)
2. 상태 및 데이터 흐름
2.1 페이지 진입 시 필요한 값
type PlaceDetailState = {
placeId: number;
visitId: number; // ARRIVED 상태 방문
placeSummary: PlaceSummary;
stats: PlaceStatsResponse;
reviews: ReviewResponse[];
likeStatus: 'ACTIVE' | 'CANCELLED';
};
placeId: URL 파라미터 또는 장소 검색 결과
visitId: 길찾기/도착 완료 시 백엔드나 네이티브에서 전달
웹이라면 /place/:placeId?visitId=10 형태로 넘겨줄 수 있음
placeSummary: 장소 카드에서 보여줄 이름·주소·영업시간 등
stats: GET /api/places/{placeId}/stats
reviews: GET /api/v1/reviews?placeId=...
likeStatus: GET /api/v1/likes/my?placeId=... 같은 API가 있다면 활용
없다면 리뷰에서 가져온 정보나 “좋아요 여부”를 캐시 후 추적
2.2 리뷰 작성 버튼 클릭
onClickReview

/places/:placeId/review/write 라우트로 이동 (또는 모달 오픈)
이동 시 visitId와 placeId를 쿼리나 상태로 전달
리뷰 작성 페이지/모달

textarea 140자 제한
POST /api/v1/reviews 요청 바디:
{ "placeId": 1, "visitId": 10, "text": "..." }
성공 시:
“리뷰 작성이 완료되었습니다.” 토스트
상세 페이지 리뷰 목록 갱신 (refetch)
2.3 좋아요 버튼 클릭
onClickLike
만약 현재 상태가 ACTIVE이면 DELETE /api/v1/likes/{likeId}
상태가 CANCELLED 혹은 아직 없으면 POST /api/v1/likes:
{ "placeId": 1, "visitId": 10 }
성공 시:
버튼/아이콘 상태 토글
성공 토스트 (예: “좋아요를 눌렀습니다.” / “좋아요가 취소되었습니다.”)
3. 예시 컴포넌트 구조(React)
function PlaceDetailPage() {
const { placeId } = useParams();
const visitId = useArrivedVisitId(placeId); // 도착 시점에 받은 값
const { data: placeSummary } = usePlaceSummary(placeId);
const { data: stats, refetch: refetchStats } = usePlaceStats(placeId);
const { data: reviews, refetch: refetchReviews } = useReviews(placeId);
const { data: likeStatus, refetch: refetchLike } = useLikeStatus(placeId);

const handleWriteReview = () => {
navigate(/places/${placeId}/reviews/new, { state: { visitId } });
};

const handleToggleLike = async () => {
if (likeStatus === 'ACTIVE') {
await deleteLike(likeId);
} else {
await createLike({ placeId, visitId });
}
refetchLike();
toast.success('좋아요 상태가 변경되었습니다.');
};

return (
<div>
<PlaceHero summary={placeSummary} />
<div className="action-buttons">
<Button onClick={handleWriteReview}>📝 리뷰 쓰기</Button>
<Button onClick={handleToggleLike}>
{likeStatus === 'ACTIVE' ? '👍 좋아요 취소' : '👍 좋아요'}
</Button>
</div>
<PlaceStatsSection stats={stats} onRefresh={refetchStats} />
<ReviewList reviews={reviews} onReload={refetchReviews} />
</div>
);
}
useArrivedVisitId는 네이티브/백엔드에서 전달받은 visitId를 불러오는 커스텀 훅
usePlaceSummary, usePlaceStats, useReviews, useLikeStatus는 각각 API 호출용 커스텀 훅
4. UX 고려사항
리뷰 작성 화면

A/B: 별도 페이지 vs 모달
제출 시 자동으로 리스트에 최신 리뷰가 추가되도록 refetch 사용
실패 시 상세한 에러 메시지 (도착 확인 안 됨, 중복 리뷰 등) 표시
좋아요 버튼

아이콘/텍스트 변경으로 상태 피드백
버튼 disable 상태 처리 (API 요청 중)
통계 KPI

버튼 아래 2×2 카드로 배치
차트/추세가 필요하다면 stats 응답에서 추가 필드를 고려
로그/모니터링

프론트에서는 단순히 메시지를 띄우지만, 백엔드에서는 누적 신고 수에 따라 자동 삭제 처리
UI에서는 “신고 완료” 토스트만 보이도록 단순화
5. Mock 데이터로 시연
안내 페이지나 프로토타입에서는 JSON mock 파일을 만들어 useEffect에서 상태를 채워 넣고, 버튼을 눌렀을 때 콘솔에 API 호출 시나리오를 출력하도록 구성
이렇게 실제 API가 준비되기 전에 UX 흐름을 검증할 수 있음
6. 요약
기능 사용자 행동 백엔드 요청 UI 반응
리뷰 작성 “리뷰 쓰기” 클릭 → 폼 작성 → 제출 POST /api/v1/reviews 토스트 + 목록 리로드
좋아요 “좋아요” 클릭 POST /api/v1/likes / DELETE /api/v1/likes/{id} 상태 토글 + 토스트
통계 페이지 진입/새로고침 GET /api/places/{placeId}/stats KPI 카드/차트 갱신
리뷰 목록 페이지 진입 GET /api/v1/reviews?placeId= 카드 리스트 표시
이 구조대로 프로토타입을 만들면 사용자가 “도착한 장소 상세 페이지에서 버튼으로 즉시 리뷰/좋아요를 등록한다”는 시나리오를 충분히 체험할 수 있습니다.

그럼백엔드는 어떻게 구현해야할까?

프런트 흐름에 맞추려면 백엔드에서 “도착한 방문(visit)”을 자동으로 찾아 주면 됩니다. 현재 API는 visitId를 직접 받아서 처리하지만, 실제 앱에서는 도착(ARRIVED) 상태의 visit을 이미 알고 있으므로 이를 서버가 대신 매칭하도록 바꾸는 것이 자연스럽습니다.

리뷰 작성 API 개선
기존
POST /api/v1/reviews
{
"placeId": 1,
"visitId": 10,
"text": "..."
}
개선 방향
visitId를 선택 항목(optional)으로 만들고, 클라이언트는 기본적으로 placeId와 본문만 보내도록 단순화합니다.
서버는 visitRepository에서 다음 정책으로 visit을 찾아 사용합니다.
해당 사용자(userId) + placeId + state = ARRIVED + deleted_at IS NULL
가장 최근 arrivedAt 또는 startedAt 기준으로 하나 선택
예시 코드
@Transactional
public ReviewResponseDTO createReview(UUID userId, ReviewCreateRequestDTO request) {
Long placeId = request.getPlaceId();
Long visitId = request.getVisitId();

if (visitId == null) {
visitId = visitRepository.findLatestArrivedVisit(userId, placeId)
.orElseThrow(() -> new FeedException(ErrorCode.REVIEW_NOT_ALLOWED, "도착한 방문이 없습니다"));
}

// 이후 로직 동일 (중복 체크, 금칙어, 저장)
}
프런트에서 visitId를 넘겨주면 그대로 쓰고, 넘겨주지 않으면 서버가 자동으로 최신 ARRIVED 방문을 선택합니다.
만약 여러 방문이 동시에 ARRIVED로 남아 있다면, 정책을 하나 더 정해야 합니다(예: 최신 것만 허용, 아니면 409 예외로 “방문을 명확하게 선택해 주세요” 안내).

좋아요 API 개선
기존
POST /api/v1/likes
{
"placeId": 1,
"visitId": 10
}
개선 방향
리뷰와 마찬가지로 visitId를 optional로 만들고, 서버가 최신 ARRIVED visit을 찾아 사용합니다.
좋아요 상태를 쉽게 확인하려면 /api/v1/likes/my?placeId= 같은 조회 API도 함께 준비하면 프런트가 현재 상태를 토글하기 편합니다.
@Transactional
public DailyLikeResponseDTO createLike(UUID userId, DailyLikeCreateRequestDTO request) {
Long placeId = request.getPlaceId();
Long visitId = Optional.ofNullable(request.getVisitId())
.orElseGet(() -> visitRepository.findLatestArrivedVisit(userId, placeId)
.orElseThrow(() -> new FeedException(ErrorCode.LIKE_NOT_ALLOWED, "도착한 방문이 없습니다")));

// 이후 중복 체크, 저장 로직 동일
}

방문 조회 도우미 API (GET /api/v1/visits/my)
만약 어떤 상황에서는 사용자에게 직접 방문을 선택하게 할 필요가 있다면, 오래전에 설계했던 “방문 목록 조회 API”를 함께 제공하면 됩니다.
예: /api/v1/visits/my?placeId=1&state=ARRIVED&limit=5
→ ARRIVED 상태의 최근 방문 리스트를 내려 주어, 프런트가 드롭다운으로 표시할 수 있습니다.
기본적으로는 “자동 매칭”이 되지만, 예외 상황(두 방문 모두 ARRIVED로 남아 있는 경우 등)을 처리하기 위한 보조 수단으로 활용할 수 있습니다.

에러 처리 정책
findLatestArrivedVisit에서 visit을 찾지 못하면 ErrorCode.REVIEW_NOT_ALLOWED 또는 LIKE_NOT_ALLOWED로 400 응답을 주고, 프런트는 “도착한 방문이 없습니다” 정도의 안내 문구를 띄우면 됩니다.
이미 리뷰를 작성했거나 좋아요를 눌렀다면 기존 예외(REVIEW_ALREADY_EXISTS, LIKE_ALREADY_EXISTS)를 그대로 사용합니다.

정리
API 개선 포인트
POST /api/v1/reviews visitId 선택 항목, 서버가 최신 ARRIVED visit 자동 매칭
POST /api/v1/likes 동일하게 visitId 자동 매칭
보조 API (선택) GET /api/v1/visits/my 로 최근 방문 리스트 내려줄 수 있음
이렇게 고치면 사용자는 장소 상세 페이지에서 “리뷰 쓰기 / 좋아요” 버튼만 눌러도 바로 동작하고, 백엔드는 필요한 visitId를 자동으로 찾아 처리하게 됩니다.

현재 나는 visitRepository를 모르잖아. 일단 기능구현을 위해서 간단하게 만들어서 사용해보자. 어때?

Role: 백엔드 아키텍트
전문가 협의

도메인 설계 전문가: 이미 VisitReadRepository가 존재하며(src/main/java/com/matjom/matjom/feed/repository/VisitReadRepository.java:10), existsByIdAndUserIdAndState 하나로 ARRIVED 여부만 판정하도록 간결하게 짜여 있음. 새 저장소를 추가하기보다 이 구현을 그대로 활용·확장하는 편이 구조를 어지럽히지 않음.
데이터 레이어 전문가: 만약 팀장님이 제공할 정식 Visit 리포지토리를 아직 쓸 수 없다면, 동일한 시그니처로 임시 저장소를 두어도 JPA가 알아서 프록시를 만들어 주니 추가 구현이 사실상 필요 없다. 엔티티 전부를 읽어 오지 않고 EXISTS 형태라 성능도 충분하다.
QA 전문가: 현 구조를 유지하면 서비스·테스트가 이미 이 메서드 기반으로 정리돼 있어 회귀 위험이 없다. 새로운 저장소를 만들면 테스트와 문서를 다시 손봐야 하니 현재 구성을 유지하는 것이 안정적이다.
추천
지금은 VisitReadRepository를 바로 주입받아 쓰면 필요한 자격 검사(ARRIVED 확인)가 해결됩니다. 팀장님 쪽에서 정식 Visit 모듈을 전달받기 전까지도 추가 코드 없이 동작하니, 임시 저장소를 새로 만들 이유는 없습니다. 만약 이름만 혼란스럽다면, 같은 파일에 아래처럼 주석을 보강하는 정도로 정리해 두세요.

@Query("""
SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
FROM Visit v
WHERE v.id = :visitId
AND v.user.id = :userId
AND v.state = :state
AND v.deletedAt IS NULL
""")
// 9월 30일 최종: ARRIVED 방문 존재 여부만 확인하는 경량 검증
boolean existsByIdAndUserIdAndState(@Param("visitId") Long visitId,
@Param("userId") UUID userId,
@Param("state") VisitState state);
이렇게 두면 리뷰·좋아요 서비스가 지금처럼 visitEligibilityChecker.check(…) → exists…(ARRIVED) 흐름을 그대로 유지할 수 있습니다. 

좋아요 아이콘을 단순히 “누르면 서버에 성공만 확인”하는 흐름에 맞춰, 프런트에서는 상태 저장 + API 호출 + 재조회(refetch) 세 단계를 구현하면 됩니다. 예시는 React + React Query 조합 기준입니다.

// hooks/usePlaceDetail.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';

export function usePlaceDetail(placeId: number) {
  return useQuery(['placeDetail', placeId], async () => {
    const { data } = await axios.get(`/api/places/${placeId}/detail`);
    return data.data;      // { statistics, reviews, totalReviewCount, liked, likeId ... }
  });
}

async function postLike(placeId: number, visitId?: number) {
  await axios.post('/api/v1/likes', { placeId, visitId });
}

async function deleteLike(likeId: string) {
  await axios.delete(`/api/v1/likes/${likeId}`);
}

export function useLikeMutations(placeId: number, visitId?: number) {
  const qc = useQueryClient();

  const likeMutation = useMutation(() => postLike(placeId, visitId), {
    onSuccess: () => qc.invalidateQueries(['placeDetail', placeId]),
  });

  const unlikeMutation = useMutation((likeId: string) => deleteLike(likeId), {
    onSuccess: () => qc.invalidateQueries(['placeDetail', placeId]),
  });

  return { likeMutation, unlikeMutation };
}
// components/PlaceDetailPage.tsx
import { usePlaceDetail, useLikeMutations } from '../hooks/usePlaceDetail';

export default function PlaceDetailPage({ placeId, visitId }: { placeId: number; visitId?: number }) {
  const { data, isLoading } = usePlaceDetail(placeId);
  const { likeMutation, unlikeMutation } = useLikeMutations(placeId, visitId);

  if (isLoading || !data) return <div>로딩 중…</div>;

  const handleToggleLike = () => {
    if (data.liked && data.likeId) {
      unlikeMutation.mutate(data.likeId);
    } else {
      likeMutation.mutate();
    }
  };

  return (
    <section>
      {/* 히어로/상세 카드 */}
      <header>…</header>

      {/* 액션 버튼 */}
      <div>
        <button onClick={handleToggleLike} disabled={likeMutation.isLoading || unlikeMutation.isLoading}>
          {data.liked ? '👍 좋아요 취소' : '👍 좋아요'}
        </button>
      </div>

      {/* 통계 / 리뷰 리스트 등 */}
      <PlaceStats stats={data.statistics} />
      <ReviewList reviews={data.reviews} />
    </section>
  );
}
구현 포인트
visitId 전달: 도착한 방문이 하나로 명확하다면 visitId를 전달하고, 모르면 생략해도 백엔드가 최신 ARRIVED 방문을 찾아 처리합니다.
성공 처리: API가 ApiResponse.ok()만 돌려주므로 UI에서는 별도 데이터 없이 invalidateQueries(또는 refetch)로 좋아요 상태를 갱신합니다.
실패 처리: useMutation의 onError에서 “도착한 방문이 없습니다” 등 백엔드 오류 메시지를 바로 토스트로 보여주면 UX가 맞춰집니다.
ID 보관: 취소/재활성화 호출에 필요한 likeId는 PlaceDetail 응답에 포함해두고(서버에서 최신 좋아요를 찾아 내려주는 형태) 버튼 토글 시 사용합니다.
상태 표시: 버튼 텍스트/색상은 data.liked에 따라 결정, API 호출 중에는 disabled로 중복 요청을 막습니다.
이 흐름 그대로 적용하면 프런트는 “좋아요 버튼 클릭 → API 호출 → 다시 조회 → 아이콘 상태 업데이트” 라인을 간결하게 유지할 수 있습니다.

맞습니다. 프론트에서 장소 상세 페이지에 진입할 때 자동으로 GET /api/places/{placeId}/stats를 호출하도록 구성하면, 사용자가 별도의 버튼을 누르지 않아도 통계 카드가 즉시 나타납니다. PlaceStatisticsController는 그 요청만 받으면 곧바로 통계 DTO를 내려주기 때문에, 프론트는 페이지 마운트 때 해당 API를 호출해 데이터를 렌더링하면 됩니다.

그거 구현하려면 어떻게 하는 거야?


React 예시로 설명할게요. 핵심은 “장소 상세 페이지가 렌더될 때 GET /api/places/{placeId}/stats를 자동 호출해 상태에 저장하고 카드에 뿌리는 것”입니다.

1. 통계 훅 준비
// hooks/usePlaceStats.ts
import { useQuery } from '@tanstack/react-query';
import axios from 'axios';

export function usePlaceStats(placeId: number) {
  return useQuery(['placeStats', placeId], async () => {
    const { data } = await axios.get(`/api/places/${placeId}/stats`);
    return data; // PlaceStatsResponseDTO 그대로
  });
}
React Query를 쓰면 컴포넌트가 마운트될 때 자동으로 호출하고 캐싱/재시도까지 관리해 줍니다.
다른 HTTP 클라이언트(예: fetch)를 써도 상관없지만, 비동기 상태 관리 라이브러리를 쓰면 편합니다.
2. 상세 페이지에서 자동 호출
// pages/PlaceDetailPage.tsx
import { useParams } from 'react-router-dom';
import { usePlaceStats } from '../hooks/usePlaceStats';
import { usePlaceDetail } from '../hooks/usePlaceDetail'; // 통계+리뷰를 한 번에 불러오는 Facade라도 좋음

export default function PlaceDetailPage() {
  const { placeId: placeIdParam } = useParams();
  const placeId = Number(placeIdParam);

  const { data: stats, isLoading: statsLoading, isError: statsError } = usePlaceStats(placeId);

  if (statsLoading) return <div>통계 불러오는 중…</div>;
  if (statsError || !stats) return <div>통계 정보를 가져오지 못했습니다.</div>;

  return (
    <section>
      {/* 히어로/기본 정보 */}
      <h1>{stats.placeName}</h1>

      {/* 통계 카드 */}
      <div className="stats-grid">
        <StatCard label="누적 방문자" value={stats.totalVisitors} />
        <StatCard label="누적 좋아요" value={stats.totalLikes} />
        <StatCard label="11~12시 평균 방문" value={stats.arrivals11To12} />
        <StatCard label="12~13시 평균 방문" value={stats.arrivals12To13} />
      </div>

      {/* 이하 리뷰 목록, 좋아요 버튼, 신고 등 */}
    </section>
  );
}
페이지 로딩 시 usePlaceStats(placeId)가 자동으로 API를 호출하여 응답을 stats로 내려줍니다.
응답 구조는 PlaceStatsResponseDTO와 동일하니, 원하는 카드에 그대로 보여주면 됩니다.
3. Facade API로 한 번에 받을 수도 있음
이미 GET /api/places/{placeId}/detail Facade를 만들었기 때문에, 통계와 리뷰를 한 번에 받고 싶다면 이 API에서 statistics 필드를 꺼내 렌더링해도 됩니다.

const { data: detail } = usePlaceDetail(placeId); // detail.statistics, detail.reviews
const stats = detail?.statistics;
4. 예외 처리/UX 팁
로딩: 통계 카드 영역에 skeleton이나 “불러오는 중…” 메시지를 잠깐 보여주세요.
실패: 네트워크 장애 혹은 장소가 없는 경우 “통계를 불러오지 못했습니다”처럼 안내하고, 필요하다면 Retry 버튼을 띄울 수 있습니다.
갱신 주기: 값은 자정 배치 이후 하루 단위로만 바뀌므로 자동으로 재조회할 필요는 없지만, React Query의 staleTime을 넉넉히 주거나 페이지 전환 시마다 새로 받도록 설정할 수 있습니다.
요약하면, 상세 페이지 마운트 시 GET /api/places/{placeId}/stats를 호출해서 상태에 담고, 카드 컴포넌트에서 표시하면 됩니다. 이미 만든 Facade를 사용하면 통계+리뷰를 묶어 받을 수도 있습니다.

현재 백엔드에서는 이미 GET /api/places/{placeId}/stats가 준비되어 있어 프런트가 페이지 진입 시 자동 호출만 하면 됩니다. 이 흐름을 위해 백엔드에서 추가 수정할 부분은 없습니다. 대신 프런트 컴포넌트에서 다음 패턴으로 API를 호출해 리스트를 렌더링하면 돼요:

const { data: stats } = useQuery(['placeStats', placeId], () =>
  axios.get(`/api/places/${placeId}/stats`).then(res => res.data)
);
이렇게 페이지 마운트 때 통계를 불러오게 하시면 사용자가 별도 버튼을 누르지 않아도 카드가 자동으로 채워집니다.

프론트엔드에서 /api/batch/*와 /api/places/{id}/detail 응답 파싱이 success/data/timestamp 구조를 따르는지 확인해 주세요. 이거 어떻게 확인해? 그리고 DailyStatsBatchService파일을 보면 주석에 캐시무효화와 예측 트리거까지 처리한다라고 나와있는데, 현재 내가 구현하는 통계자료에는 캐시가 없고, 에측 기반으로 돌려주는 자료가 없는데, runAggregation메소드 다시 확인해 그리고 DaiyStatPredictionService 있잖아. 뭘 ㅇ예측하는거지?


프런트 검증 방법

프론트 코드 확인: /api/batch/midnight-reset, /api/batch/status/last, /api/places/{id}/detail 호출부에서 response.success, response.data, response.timestamp를 참조하는지 살펴보세요. React Query나 axios 호출부를 직접 열어 현재 구조를 파싱하는지 확인하는 게 가장 빠릅니다.
실행 후 네트워크 캡처: 앱을 띄운 뒤 개발자 도구(Network 탭)로 해당 API 응답을 확인하고, 프런트 로직이 data 속성 아래 값을 읽는지 콘솔 로그나 브레이크포인트로 검증할 수 있습니다.
임시 로그 추가: 호출부에 console.log(response);를 찍어서 실제 응답이 공통 포맷으로 오고, 프런트가 제대로 파싱하는지 바로 확인해도 됩니다.
DailyStatsBatchService.runAggregation 재확인

실제 수행 작업은 “전일 방문/리뷰/좋아요/시간대 데이터를 수집 → place_daily_stats에 UPSERT → DailyStatsSummary를 모아 예측 훅에 전달”입니다.
현재 캐시 계층을 제거했기 때문에 placeStatsCacheService.evict(placeId); 는 이미 삭제되어 있습니다. (코드 상에서도 사라져 있는지 다시 한 번 확인해주세요.)
따라서 주석도 “캐시 무효화와 예측 트리거”라고 되어 있으면 일부 내용을 수정해 “집계 및 예측 트리거” 정도로 명확히 바꾸는 게 좋습니다.
DailyStatsPredictionService는 무엇을 예측하나?

지금은 Stub 형태이고, statistics.prediction.enabled=true인 경우에만 REST 호출을 보냅니다.
목적은 “일일 집계가 끝난 뒤, 그 데이터를 외부 예측 모델(혼잡도 예측 등)에 넘겨 재학습/업데이트를 트리거”하는 것입니다.
현재 우리 프로젝트에서는 실제 예측 모델이 연결되어 있지 않으므로, 기본 설정(enabled=false)에서는 아무 동작도 하지 않습니다. 나중에 통계 기반 추천/혼잡도 예측을 붙일 때 사용할 확장 포인트라고 보면 됩니다.


프론트 쪽에서 hourlyArrivals 배열을 제대로 쓰고 있는지 확인하려면 다음 순서를 따라주세요.

현재 화면에서 통계 데이터를 읽는 부분 찾기

GET /api/places/{id}/stats 호출 후 응답을 파싱하는 컴포넌트/서비스 파일을 열어 data.arrivals11To12 같은 필드 접근이 남아있는지 확인하세요. React라면 usePlaceStats, PlaceStatsSection, PlaceDetailPage 같은 훅/컴포넌트가 후보입니다.
새 응답 구조에 맞게 매핑 수정하기

기존에 arrivals11To12, arrivals12To13 등을 UI 카드에 바인딩했다면 data.hourlyArrivals 배열을 사용하도록 바꿔야 합니다.
예시:
const stats = data.hourlyArrivals ?? [];
return stats.map(({ hour, averageCount }) => (
  <StatsItem key={hour} hourLabel={`${hour}:00`} value={averageCount} />
));
시각적으로 11~20시만 표기해야 하니, 배열이 비어 있을 경우 대비해 기본값을 넣거나 filter로 정제할 수 있습니다.
타입 정의나 모델 업데이트

TypeScript 사용 시 PlaceStatsResponse 타입을 hourlyArrivals: Array<{hour: number; averageCount: number;}>;로 갱신하고, 더 이상 쓰지 않는 필드를 제거합니다.
UI 구성 검증

통계 카드/차트가 새 배열 기반으로 렌더링되는지 확인합니다.
단순 카드라면 map으로 반복 렌더링, 차트라면 x축 라벨과 y축 데이터를 hourlyArrivals에서 추출해 넘깁니다.
테스트/스토리 점검

Jest/Storybook이 있다면 mock 응답을 새 구조로 바꾸고, 화면에 10개(11~20시) 항목이 나타나는지 스냅샷 또는 DOM 검증을 추가합니다.
통합 확인

실제 API를 호출하거나 MSW 같은 목 서버를 이용해 hourlyArrivals 필드가 잘 출력되는지 브라우저에서 확인합니다.
이 과정을 따라가면 프론트에서 새 통계 구조를 제대로 소비하고 있는지 빠르게 검증할 수 있습니다.