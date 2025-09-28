# 리뷰 신고 API 가이드 (9월 30일 최종)

## 개요
- **엔드포인트:** `POST /api/v1/reviews/{reviewId}/reports`
- **목적:** 사용자가 특정 리뷰에 대해 신고를 접수하면 신고 이력을 저장하고 누적 신고 건수를 반환합니다.
- **권한:** JWT 인증 필수. 컨트롤러는 `@RequestAttribute("userId")`로 신고자 UUID를 전달받습니다.

## 요청 스펙
```http
POST /api/v1/reviews/{reviewId}/reports
Content-Type: application/json
Authorization: Bearer {JWT}
```

```json
{
  "reason": "SPAM",            // 필수: ReportReason ENUM (SPAM, INAPPROPRIATE, FAKE, OFFENSIVE, OTHER)
  "description": "홍보성 댓글"  // 선택: 상세 설명 (최대 500자)
}
```

## 응답 스펙
```json
{
  "reportId": "b7a4c6f2-8b2e-4ce0-921f-9b6fca6b9cbe",
  "reason": "SPAM",
  "description": "홍보성 댓글",
  "reportedAt": "2025-09-30T12:45:21.123Z",
  "reportCount": 3
}
```

- `reportId`: 새로 생성된 신고 이력 UUID
- `reason`: 요청과 동일한 신고 사유 ENUM 값
- `description`: 신고자가 전달한 설명 (null 가능)
- `reportedAt`: 신고가 저장된 시각 (UTC, `OffsetDateTime`)
- `reportCount`: 해당 리뷰의 누적 신고 건수 (이번 신고 포함)

> **Note** `reviewId`나 `reporterName`은 응답에서 제외되었습니다. 프런트는 요청 시 사용한 `reviewId`를 그대로 유지하고, 신고 완료 토스트/모달에 `reportCount`만 표시하면 됩니다.

## 예외 응답 요약

| 상황 | HTTP | 에러 코드 | 메시지 |
| ---- | ---- | --------- | ------- |
| 동일 사용자가 이미 신고한 경우 | 400 | `REVIEW_REPORT_ALREADY_EXISTS` | "이미 신고한 리뷰입니다." |
| 리뷰가 존재하지 않거나 삭제된 경우 | 404 | `REVIEW_NOT_FOUND` | "리뷰를 찾을 수 없습니다." |

공통 에러 포맷은 `공통응답_가이드.md`를 참고하세요.

## 프런트 핸들링 메모
1. 신고 성공 시 완료 메시지와 `reportCount` 표시 (예: "신고가 접수되었습니다. 현재 신고 3건")
2. 동일 사용자의 재신고는 400 응답으로 차단되므로 에러 메시지를 토스트로 노출
3. 삭제된 리뷰를 신고하려는 경우 404 응답을 받아 처리

