package com.matjom.matjom.common.security.captcha;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecaptchaVerificationResponse {

    private boolean success;

    private String hostname;

    @JsonAlias("challenge_ts")
    private String challengeTimestamp;

    @JsonAlias("error-codes")
    private List<String> errorCodes;
}
