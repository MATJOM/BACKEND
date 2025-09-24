package com.matjom.matjom.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CaptchaService {

    public boolean verify(String token) {
        return StringUtils.hasText(token);
    }
}
