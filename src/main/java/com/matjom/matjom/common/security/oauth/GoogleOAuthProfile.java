package com.matjom.matjom.common.security.oauth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GoogleOAuthProfile {
    private final String email;
    private final String name;
    private final String subject;
    private final String picture;
}
