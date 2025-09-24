package com.matjom.matjom.common.security.captcha;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Holds Google reCAPTCHA server-side configuration values.
 * Falls back to environment variables when application configuration is absent.
 */
@Getter
@Component
public class RecaptchaProperties {

    private final String secret;
    private final String verifyUrl;

    public RecaptchaProperties(
            @Value("${captcha.google.secret:${CAPTCHA_GOOGLE_SECRET:}}") String secret,
            @Value("${captcha.google.verify-url:https://www.google.com/recaptcha/api/siteverify}") String verifyUrl
    ) {
        this.secret = secret;
        this.verifyUrl = verifyUrl;
    }

    public boolean hasSecret() {
        return secret != null && !secret.isBlank();
    }
}
