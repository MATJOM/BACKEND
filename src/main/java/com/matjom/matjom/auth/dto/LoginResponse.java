package com.matjom.matjom.auth.dto;

import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private String email;
    private String name;
    private AuthProvider provider;
    private String refreshToken;
    public static LoginResponse from(User user, String refreshToken) {
        return LoginResponse.builder()
                .email(user.getEmail())
                .name(user.getName())
                .provider(user.getProvider())
                .refreshToken(refreshToken)
                .build();
    }
}

