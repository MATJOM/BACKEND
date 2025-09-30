package com.matjom.matjom.auth.dto;

import com.matjom.matjom.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {

    private String name;

    public static LoginResponse from(User user) {
        return LoginResponse.builder()
                .name(user.getName())
                .build();
    }
}
