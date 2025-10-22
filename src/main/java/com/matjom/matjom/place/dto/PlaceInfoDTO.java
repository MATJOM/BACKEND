package com.matjom.matjom.place.dto;

import com.matjom.matjom.place.entity.Place;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장소 기본 정보를 표현하는 DTO.
 * 사용 목적: 상세 화면의 헤더(이름, 주소, 카테고리, 운영시간, 연락처)를 한 번에 전달한다.
 * 코드 의미: `Place` 엔티티의 핵심 필드와 주소 문자열을 매핑하고, Jackson `JsonNode`를 그대로 노출해 프런트가 구조를 재사용하도록 한다.
 * 기대 결과: 통계/리뷰가 실패하더라도 최소한의 장소 정보는 항상 제공된다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceInfoDTO {
    private Long placeId;
    private String name;
    private String address;
    private List<String> categories;
    private JsonNode workingHours;
    private JsonNode breakTime;
    private String phoneNumber;

    /**
     * `Place` 엔티티에서 DTO로 변환한다.
     * 사용 목적: 서비스 레이어가 엔티티·주소 문자열을 조립해 응답 모델로 전달한다.
     * 코드 의미: 리스트/JSON은 그대로 복사하고, 주소는 매개변수로 받은 가공된 문자열을 사용한다.
     * 기대 결과: 한 곳에서만 필드 매핑을 관리해 누락/오타를 방지한다.
     */
    public static PlaceInfoDTO from(Place place, String address) {
        return PlaceInfoDTO.builder()
                .placeId(place.getId())
                .name(place.getName())
                .address(address)
                .categories(place.getCategory())
                .workingHours(place.getWorkingHours())
                .breakTime(place.getBreakTime())
                .phoneNumber(place.getPhoneNumber())
                .build();
    }
}
