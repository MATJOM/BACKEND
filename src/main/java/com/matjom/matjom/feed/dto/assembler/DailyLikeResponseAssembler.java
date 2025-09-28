package com.matjom.matjom.feed.dto.assembler;

import com.matjom.matjom.feed.dto.response.DailyLikeResponseDTO;
import com.matjom.matjom.feed.entity.likes.DailyLike;
import com.matjom.matjom.feed.repository.PlaceReadRepository;
import com.matjom.matjom.feed.repository.UserReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailyLikeResponseAssembler {

    private static final String UNKNOWN = "알 수 없음";

    private final UserReadRepository userReadRepository;
    private final PlaceReadRepository placeReadRepository;

    // 목적: 좋아요 엔티티를 사용자·장소 이름이 포함된 DTO로 변환한다
    // 필요 이유: 응답에서 UUID 대신 읽기 쉬운 정보를 제공하기 위함이다
    // 로직: 사용자/장소 이름을 조회해 DTO 팩터리에 전달한다
    public DailyLikeResponseDTO toDto(DailyLike like) {
        String userName = fetchUserName(like.getUserId());
        String placeName = fetchPlaceName(like.getPlaceId());
        return DailyLikeResponseDTO.of(like, userName, placeName);
    }

    // 목적: 사용자 이름 조회 실패 시 기본 문자열을 사용한다
    // 필요 이유: 조회 에러가 발생해도 API는 안정적으로 동작해야 한다
    // 로직: 이름 조회 중 예외를 캐치해 로그 후 기본값을 반환한다
    private String fetchUserName(java.util.UUID userId) {
        try {
            return userReadRepository.findNameById(userId).orElse(UNKNOWN);
        } catch (DataAccessException ex) {
            log.debug("사용자 이름 조회 실패: userId={}", userId, ex);
            return UNKNOWN;
        }
    }

    // 목적: 장소 이름을 가져오되 실패 시 기본값을 돌려준다
    // 필요 이유: DB 연결 이슈 발생 시에도 응답을 지속해야 한다
    // 로직: 조회 예외를 잡아 로그를 남기고 기본 문자열을 반환한다
    private String fetchPlaceName(Long placeId) {
        try {
            return placeReadRepository.findNameById(placeId).orElse(UNKNOWN);
        } catch (DataAccessException ex) {
            log.debug("장소 이름 조회 실패: placeId={}", placeId, ex);
            return UNKNOWN;
        }
    }
}
