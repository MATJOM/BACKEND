package com.matjom.matjom.place.service;

import com.matjom.matjom.common.exception.base.PlaceException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.place.dto.PlaceDetailResponseDTO;
import com.matjom.matjom.place.dto.PlaceInfoDTO;
import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.place.repository.PlaceReadRepository;
import com.matjom.matjom.place.entity.Place;
import com.matjom.matjom.statistics.dto.StatsResponseDTO;
import com.matjom.matjom.statistics.service.StatisticsService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.matjom.matjom.feed.service.ReviewService;

/**
 * 장소 상세(정보 + 통계 + 리뷰) 조회를 담당하는 서비스.
 * 사용 목적: 한 번의 호출로 여러 하위 모듈 결과를 조합하고, 부분 실패가 있어도 응답을 반환한다.
 * 코드 의미: 장소 엔티티 조회 → 통계/리뷰 호출 → 오류를 `ErrorDetail`로 래핑 → 주소 문자열 생성 순으로 처리한다.
 * 기대 결과: 통계 쿼리나 리뷰 쿼리 실패가 있어도 최소한 장소 정보와 실패 사유를 내려 프런트가 시연 중 중단되지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PlaceDetailService {

    private static final int DEFAULT_REVIEW_LIMIT = 15;

    private final PlaceReadRepository placeReadRepository;
    private final StatisticsService statisticsService;
    private final ReviewService reviewService;

    /**
     * 리뷰 기본 제한을 적용해 장소 상세를 조회한다.
     */
    public PlaceDetailResponseDTO getPlaceDetail(Long placeId) {
        return getPlaceDetail(placeId, null);
    }

    /**
     * 장소 상세를 조회한다.
     * 사용 목적: 통계/리뷰 조회 중 일부가 실패해도 오류를 담아 응답을 반환한다.
     * 코드 의미: 장소 정보를 먼저 로드하고, 통계·리뷰 호출은 try/catch로 감싸서 예외를 `ErrorDetail`로 변환한다.
     * 기대 결과: 예외 전파로 API 전체가 500이 되는 상황을 방지하고, 어떤 서브 모듈이 실패했는지 UI가 알 수 있다.
     */
    public PlaceDetailResponseDTO getPlaceDetail(Long placeId, Integer reviewLimit) {
        Place place = loadPlace(placeId);
        PlaceInfoDTO info = PlaceInfoDTO.from(place, buildAddress(place));

        StatsResponseDTO stats = null;
        PlaceDetailResponseDTO.ErrorDetail statsError = null;
        try {
            stats = statisticsService.fetchStats(placeId);
        } catch (Exception ex) {
            statsError = PlaceDetailResponseDTO.ErrorDetail.of("STATS_UNAVAILABLE", "통계 정보를 불러오지 못했습니다");
            log.warn("Failed to load place statistics: placeId={}", placeId, ex);
        }

        List<ReviewResponseDTO> reviews = List.of();
        PlaceDetailResponseDTO.ErrorDetail reviewError = null;
        try {
            reviews = loadReviews(placeId, reviewLimit);
        } catch (Exception ex) {
            reviewError = PlaceDetailResponseDTO.ErrorDetail.of("REVIEWS_UNAVAILABLE", "리뷰 정보를 불러오지 못했습니다");
            log.warn("Failed to load place reviews: placeId={}", placeId, ex);
        }

        Map<String, PlaceDetailResponseDTO.ErrorDetail> errors = PlaceDetailResponseDTO.errorsOf(statsError, reviewError);
        return PlaceDetailResponseDTO.of(info, stats, reviews, errors);
    }

    /**
     * 장소 기본 정보만 반환한다.
     */
    public PlaceInfoDTO getPlaceInfo(Long placeId) {
        Place place = loadPlace(placeId);
        return PlaceInfoDTO.from(place, buildAddress(place));
    }

    /**
     * 장소 리뷰만 조회한다.
     */
    public List<ReviewResponseDTO> getPlaceReviews(Long placeId, Integer reviewLimit) {
        if (!placeReadRepository.existsById(placeId)) {
            throw new PlaceException(ErrorCode.PLACE_NOT_FOUND);
        }
        return loadReviews(placeId, reviewLimit);
    }

    /**
     * 존재하는 장소인지 검증하고 엔티티를 로딩한다.
     */
    private Place loadPlace(Long placeId) {
        return placeReadRepository.findById(placeId)
                .orElseThrow(() -> new PlaceException(ErrorCode.PLACE_NOT_FOUND));
    }

    /**
     * 분리 저장된 주소 컴포넌트를 하나의 문자열로 합친다.
     * 사용 목적: 주소 포맷이 변경되거나 일부 필드가 비어 있을 때도 안전하게 처리한다.
     */
    private String buildAddress(Place place) {
        StringBuilder builder = new StringBuilder();
        append(builder, place.getAddrSido());
        append(builder, place.getAddrSigungu());
        append(builder, place.getAddrEupmyeondong());
        append(builder, place.getAddrStreet());
        append(builder, place.getAddrDetail());
        return builder.toString().trim();
    }

    /**
     * 공백과 null을 건너뛰며 주소 조각을 조인한다.
     */
    private void append(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(value);
    }

    /**
     * 리뷰 제한 값을 계산하고 서비스 호출을 위임한다.
     * 사용 목적: 프런트에서 원하는 리뷰 개수를 조절하거나 전체 목록을 요청할 수 있게 한다.
     * 코드 의미: null → 기본 15, 0 이하 → 전체 조회, 양수 → 최근 N개만 반환.
     */
    private List<ReviewResponseDTO> loadReviews(Long placeId, Integer reviewLimit) {
        int effectiveLimit = resolveLimit(reviewLimit);
        if (effectiveLimit < 0) {
            return reviewService.getPlaceReviews(placeId);
        }
        return reviewService.getLatestPlaceReviews(placeId, effectiveLimit);
    }

    /**
     * 리뷰 개수 파라미터를 안전하게 정규화한다.
     */
    private int resolveLimit(Integer reviewLimit) {
        if (reviewLimit == null) {
            return DEFAULT_REVIEW_LIMIT;
        }
        if (reviewLimit <= 0) {
            return -1;
        }
        return reviewLimit;
    }
}
