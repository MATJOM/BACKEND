package com.matjom.matjom.auth.dto;

import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReissueResponse {
    String name;
    String email;
    AuthProvider provider;

    public static ReissueResponse from(User user){
        return ReissueResponse.builder()
                .name(user.getName())
                .email(user.getEmail())
                .provider(user.getProvider())
                .build();
    }
}
