package com.matjom.matjom.auth.service;

import com.matjom.matjom.auth.dto.LoginRequest;
import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.rate.LoginRateLimiter;
import com.matjom.matjom.auth.repository.RefreshTokenRepository;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginRateLimiter loginRateLimiter;
    private final CaptchaService captchaService;

    public LoginResult login(LoginRequest request) {
        User user = authenticate(request);
        return issueTokens(user);
    }

    private User authenticate(LoginRequest request) {
        String email = request.getEmail();
        enforceCaptchaIfRequired(email, request.getCaptchaToken());

        User user = userRepository.findByEmailAndProvider(email, AuthProvider.LOCAL)
                .orElseThrow(() -> invalidCredentials(email));

        if (user.isDeleted()) {
            throw invalidCredentials(email);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw invalidCredentials(email);
        }

        return user;
    }

    public LoginResult issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = jwtTokenProvider.createRefreshToken(user);
        refreshTokenRepository.save(user.getId(), refreshToken);
        loginRateLimiter.reset(user.getEmail());

        LoginResponse response = LoginResponse.from(user);
        return LoginResult.from(accessToken, refreshToken, response);
    }

    private AuthException invalidCredentials(String email) {
        loginRateLimiter.recordFailure(email);
        if (loginRateLimiter.isCaptchaRequired(email)) {
            return new AuthException(ErrorCode.CAPTCHA_REQUIRED);
        }
        return new AuthException(ErrorCode.INVALID_CREDENTIALS);
    }

    private void enforceCaptchaIfRequired(String email, String captchaToken) {
        if (!loginRateLimiter.isCaptchaRequired(email)) {
            return;
        }
        if (!captchaService.verify(captchaToken)) {
            throw new AuthException(ErrorCode.CAPTCHA_REQUIRED);
        }
    }
}
