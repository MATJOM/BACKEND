package com.matjom.matjom.auth.service;

import com.matjom.matjom.common.security.captcha.RecaptchaProperties;
import com.matjom.matjom.common.security.captcha.RecaptchaVerificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Verifies CAPTCHA tokens by delegating to Google reCAPTCHA v2 siteverify API.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CaptchaService {

    private final RestTemplate recaptchaRestTemplate;
    private final RecaptchaProperties recaptchaProperties;

    /**
     * 구글에서 토큰이 유효하다고 선언하면 true 반환
     * 비밀번호 누락, 토큰 누락, 원격 검증 오류 시 false 반환
     */
    public boolean verify(String token) {
        if (!recaptchaProperties.hasSecret()) {
            log.warn("reCAPTCHA secret is not configured; treating verification as failed");
            return false;
        }

        if (!StringUtils.hasText(token)) {
            return false;
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("secret", recaptchaProperties.getSecret());
        body.add("response", token);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            RecaptchaVerificationResponse response = recaptchaRestTemplate.postForObject(
                    recaptchaProperties.getVerifyUrl(),
                    request,
                    RecaptchaVerificationResponse.class
            );
            boolean success = response != null && response.isSuccess();
            if (!success && response != null && response.getErrorCodes() != null) {
                log.debug("reCAPTCHA verification failed: {}", response.getErrorCodes());
            }
            return success;
        } catch (RestClientException ex) {
            log.warn("Failed to verify reCAPTCHA token", ex);
            return false;
        }
    }
}
