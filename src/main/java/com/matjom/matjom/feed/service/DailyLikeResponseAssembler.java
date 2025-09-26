package com.matjom.matjom.feed.service;

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

    public DailyLikeResponseDTO toDto(DailyLike like) {
        String userName = fetchUserName(like.getUserId());
        String placeName = fetchPlaceName(like.getPlaceId());
        return DailyLikeResponseDTO.of(like, userName, placeName);
    }

    private String fetchUserName(java.util.UUID userId) {
        try {
            return userReadRepository.findNameById(userId).orElse(UNKNOWN);
        } catch (DataAccessException ex) {
            log.debug("사용자 이름 조회 실패: userId={}", userId, ex);
            return UNKNOWN;
        }
    }

    private String fetchPlaceName(Long placeId) {
        try {
            return placeReadRepository.findNameById(placeId).orElse(UNKNOWN);
        } catch (DataAccessException ex) {
            log.debug("장소 이름 조회 실패: placeId={}", placeId, ex);
            return UNKNOWN;
        }
    }
}
