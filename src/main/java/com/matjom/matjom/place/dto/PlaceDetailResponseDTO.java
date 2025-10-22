package com.matjom.matjom.place.dto;

import com.matjom.matjom.feed.dto.response.ReviewResponseDTO;
import com.matjom.matjom.statistics.dto.StatsResponseDTO;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * `/api/v1/places/{id}` 상세 응답을 구성하는 최종 DTO.
 * 사용 목적: 장소 정보, 통계, 리뷰 목록, 하위 모듈에서 실패한 에러 메시지를 한 번에 전달한다.
 * 코드 의미: 불변 DTO 패턴을 사용해 서브 객체를 복사/정규화하고, 통계·리뷰 로딩 실패 시 `errors` 맵으로 사유를 명시한다.
 * 기대 결과: 컨트롤러가 여러 서비스 호출 결과를 안전하게 묶어 UI가 필요한 모든 정보를 단일 응답으로 렌더링한다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceDetailResponseDTO {

    private PlaceInfoDTO info;
    private StatsResponseDTO stats;
    private List<ReviewResponseDTO> reviews;
    private Map<String, ErrorDetail> errors;

    /**
     * 세부 정보를 조합해 응답 DTO를 생성한다.
     * 사용 목적: Service 레이어가 부분 실패를 체크한 뒤 안전하게 응답을 만들도록 한다.
     * 코드 의미: 리뷰 리스트를 방어 복사하고, 에러 맵은 가변 입력을 불변 Map으로 변환한다.
     * 기대 결과: 외부에서 리스트/맵을 수정해도 응답 상태가 변하지 않는다.
     */
    public static PlaceDetailResponseDTO of(PlaceInfoDTO info,
                                            StatsResponseDTO stats,
                                            List<ReviewResponseDTO> reviews,
                                            Map<String, ErrorDetail> errors) {
        return PlaceDetailResponseDTO.builder()
                .info(info)
                .stats(stats)
                .reviews(reviews == null ? List.of() : List.copyOf(reviews))
                .errors(normalizeErrors(errors))
                .build();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorDetail {
        private String code;
        private String message;

        /**
         * 에러 코드를 간단히 생성할 때 사용한다.
         */
        public static ErrorDetail of(String code, String message) {
            return new ErrorDetail(code, message);
        }
    }

    /**
     * 통계/리뷰 모듈에서 발생한 예외를 응답에 포함시킨다.
     * 사용 목적: UI가 어떤 섹션이 실패했는지 구분해 토스트/배지를 보여줄 수 있도록 한다.
     * 코드 의미: stats/reviews 키를 고정해 내려주고, null 값은 정규화 단계에서 제거한다.
     */
    public static Map<String, ErrorDetail> errorsOf(ErrorDetail statsError, ErrorDetail reviewError) {
        Map<String, ErrorDetail> map = new LinkedHashMap<>();
        map.put("stats", statsError);
        map.put("reviews", reviewError);
        return normalizeErrors(map);
    }

    /**
     * null 또는 빈 맵을 방어적으로 처리해 불변 구조로 반환한다.
     */
    private static Map<String, ErrorDetail> normalizeErrors(Map<String, ErrorDetail> errors) {
        if (errors == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }
}
