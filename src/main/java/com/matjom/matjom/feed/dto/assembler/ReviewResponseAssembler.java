package com.matjom.matjom.feed.dto.assembler;

import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.feed.entity.review.Review;
import com.matjom.matjom.feed.repository.UserReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewResponseAssembler {

    private static final String UNKNOWN = "알 수 없음"; // 9월 26일 최종: 기본 문자열

    private final UserReadRepository userReadRepository;

    // 목적: 리뷰 엔티티를 응답 DTO로 변환한다
    // 필요 이유: userId 대신 사람이 읽기 쉬운 작성자 이름을 포함해야 한다
    // 로직: 사용자 이름을 안전하게 로드해 DTO 팩토리 메서드에 전달한다
    public ReviewResponseDTO toDto(Review review) {
        String reviewerName = loadReviewerName(review.getUserId());
        return ReviewResponseDTO.of(review, reviewerName); // 9월 30일 최종: 작성자 이름과 본문만 포함
    }

    // 목적: 사용자 이름을 조회하되 실패 시 기본 문자열을 제공한다
    // 필요 이유: DB 접근 오류가 있어도 API 응답이 중단되지 않도록 하기 위함이다
    // 로직: 이름 조회 시 예외를 캐치하고 로그 후 기본값을 반환한다
    private String loadReviewerName(java.util.UUID userId) {
        try {
            return userReadRepository.findNameById(userId).orElse(UNKNOWN);
        } catch (DataAccessException ex) {
            log.debug("사용자 이름 조회 실패: userId={}", userId, ex);
            return UNKNOWN;
        }
    }
}
