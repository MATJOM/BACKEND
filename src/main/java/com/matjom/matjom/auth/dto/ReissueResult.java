package com.matjom.matjom.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReissueResult {
    private String accessToken;
    private ReissueResponse response;

    public static ReissueResult from(String accessToken, ReissueResponse response) {
        return ReissueResult.builder()
                .accessToken(accessToken)
                .response(response)
                .build();
    }
}
