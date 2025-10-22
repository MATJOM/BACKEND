package com.matjom.matjom.moderation.profanity;

import com.matjom.matjom.common.exception.base.FeedException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * 하드코딩된 금칙어 목록으로 검증하는 필터 구현체.
 * 사용 목적: 외부 리소스 없이 빠르게 금칙어 검증 기능을 제공한다.
 * 코드 의미: Set 컬렉션에 금칙어를 정의하고 문자열 전처리 후 포함 여부를 검사한다.
 * 기대 결과: 금칙어가 발견되면 즉시 도메인 예외를 던져 리뷰 작성이 차단된다.
 */

@Slf4j
public class HardcodedProfanityFilter implements ProfanityFilter {

    private final Set<String> blacklist = Set.of("욕설1", "욕설2", "비속어3");

    @Override
    /**
     * 금칙어가 포함되었는지 검사한다.
     * 사용 목적: 리뷰 작성/수정 시 부적절한 표현을 차단한다.
     * 코드 의미: 내부 `hasBlacklistedWord` 검사 결과가 true면 예외를 발생시킨다.
     * 기대 결과: 금칙어 포함 시 FeedException이 던져지고 호출 측이 처리한다.
     */
    public void validate(String text) {
        if (hasBlacklistedWord(text)) {
            throw new FeedException(ErrorCode.REVIEW_BAD_LANGUAGE, "금칙어가 포함되어 있습니다.");
        }
    }

    /**
     * 입력 문자열에 금칙어가 포함되어 있는지 확인한다.
     * 사용 목적: 필터링 로직을 캡슐화해 재사용한다.
     * 코드 의미: 문자열을 정규화한 뒤 금칙어 Set 중 하나라도 포함되면 true를 반환한다.
     * 기대 결과: 금칙어가 있으면 true, 없으면 false를 반환해 상위 로직에서 분기한다.
     */
    private boolean hasBlacklistedWord(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase().replaceAll("[^가-힣a-z0-9]", "");
        return blacklist.stream().anyMatch(normalized::contains);
    }
}
