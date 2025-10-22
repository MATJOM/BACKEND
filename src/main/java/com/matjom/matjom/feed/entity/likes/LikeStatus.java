package com.matjom.matjom.feed.entity.likes;

/**
 * 좋아요 상태를 표현하는 열거형.
 * 사용 목적: 좋아요 활성/취소 상태를 명시적으로 구분해 비즈니스 로직에서 사용한다.
 * 코드 의미: ACTIVE와 CANCELLED 두 가지 상태만 정의해 상태 전환 로직을 단순화한다.
 * 기대 결과: 엔티티와 서비스에서 상태값 비교가 명확해져 조건 분기를 줄인다.
 */
public enum LikeStatus {
    ACTIVE,
    CANCELLED
}
