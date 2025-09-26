package com.matjom.matjom.common.security.oauth;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleTokenInfoResponse {

    private String aud;
    private String email;

    @JsonAlias("email_verified")
    private String emailVerified;

    private String name;
    private String picture;
    private String sub;

    @JsonProperty("exp")
    private String expiresAt;

    public boolean isEmailVerified() {
        return "true".equalsIgnoreCase(emailVerified);
    }
}
