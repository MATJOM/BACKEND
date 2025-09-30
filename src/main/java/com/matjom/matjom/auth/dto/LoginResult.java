package com.matjom.matjom.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResult {
    private String accessToken;
    private String refreshToken;
    private LoginResponse response;

    public static LoginResult from(String accessToken, String refreshToken, LoginResponse response) {
        return LoginResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .response(response)
                .build();
    }
}
