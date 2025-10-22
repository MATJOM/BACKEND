package com.matjom.matjom.moderation.report.entity;

/**
 * 리뷰 신고 유형을 정의하는 열거형.
 * 사용 목적: 신고 사유 입력을 제한된 카테고리로 표준화한다.
 * 코드 의미: 스팸, 부적절, 허위, 공격적, 기타 다섯 가지 값을 제공한다.
 * 기대 결과: 신고 저장 시 명확한 사유 분류가 가능해진다.
 */
public enum ReportReason {
    SPAM,
    INAPPROPRIATE,
    FAKE,
    OFFENSIVE,
    OTHER
}
