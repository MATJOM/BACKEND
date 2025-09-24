package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.dto.LoginRequest;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.rate.LoginRateLimiter;
import com.matjom.matjom.auth.repository.RefreshTokenRepository;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final String NAME = "User";
    private static final String RAW_PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "encoded-password";
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private LoginRateLimiter loginRateLimiter;

    @Mock
    private CaptchaService captchaService;

    @InjectMocks
    private LoginService loginService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = createUser(false);
    }

    @Test
    void login_success_withoutCaptcha() {
        LoginRequest request = createRequest(null);

        when(loginRateLimiter.isCaptchaRequired(EMAIL)).thenReturn(false);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(activeUser)).thenReturn(ACCESS_TOKEN);
        when(jwtTokenProvider.createRefreshToken(activeUser)).thenReturn(REFRESH_TOKEN);

        LoginResult result = loginService.login(request);

        assertThat(result.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(result.getResponse().getRefreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(result.getResponse().getEmail()).isEqualTo(EMAIL);
        assertThat(result.getResponse().getName()).isEqualTo(NAME);
        assertThat(result.getResponse().getProvider()).isEqualTo(AuthProvider.LOCAL);

        verify(refreshTokenRepository).save(USER_ID, REFRESH_TOKEN);
        verify(loginRateLimiter).reset(EMAIL);
        verify(loginRateLimiter, times(1)).isCaptchaRequired(EMAIL);
        verify(captchaService, never()).verify(any());
    }

    @Test
    void login_success_withCaptchaVerification() {
        String captchaToken = "captcha-token";
        LoginRequest request = createRequest(captchaToken);

        when(loginRateLimiter.isCaptchaRequired(EMAIL)).thenReturn(true);
        when(captchaService.verify(captchaToken)).thenReturn(true);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(activeUser)).thenReturn(ACCESS_TOKEN);
        when(jwtTokenProvider.createRefreshToken(activeUser)).thenReturn(REFRESH_TOKEN);

        LoginResult result = loginService.login(request);

        assertThat(result.getAccessToken()).isEqualTo(ACCESS_TOKEN);
        verify(captchaService).verify(captchaToken);
        verify(loginRateLimiter).reset(EMAIL);
    }

    @Test
    void login_invalidPassword_withoutCaptchaRequirement() {
        LoginRequest request = createRequest(null);

        when(loginRateLimiter.isCaptchaRequired(EMAIL)).thenReturn(false);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class, () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        verify(loginRateLimiter, times(2)).isCaptchaRequired(EMAIL);
        verify(loginRateLimiter).recordFailure(EMAIL);
        verifyNoInteractions(refreshTokenRepository);
        verify(loginRateLimiter, never()).reset(EMAIL);
    }

    @Test
    void login_invalidPassword_triggersCaptchaRequirementAfterFailures() {
        LoginRequest request = createRequest(null);

        when(loginRateLimiter.isCaptchaRequired(EMAIL)).thenReturn(false, true);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class, () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CAPTCHA_REQUIRED);
        verify(loginRateLimiter, times(2)).isCaptchaRequired(EMAIL);
        verify(loginRateLimiter).recordFailure(EMAIL);
        verify(captchaService, never()).verify(any());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void login_requiresCaptcha_butVerificationFails() {
        LoginRequest request = createRequest(null);

        when(loginRateLimiter.isCaptchaRequired(EMAIL)).thenReturn(true);
        when(captchaService.verify(null)).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class, () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CAPTCHA_REQUIRED);
        verify(loginRateLimiter, times(1)).isCaptchaRequired(EMAIL);
        verify(captchaService).verify(null);
        verifyNoInteractions(userRepository);
        verifyNoInteractions(refreshTokenRepository);
    }

    private LoginRequest createRequest(String captchaToken) {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword(RAW_PASSWORD);
        request.setCaptchaToken(captchaToken);
        return request;
    }

    private User createUser(boolean deleted) {
        User user = User.createLocalUser(EMAIL, NAME, ENCODED_PASSWORD);
        setField(user, "id", USER_ID);
        if (deleted) {
            user.markDeleted();
        }
        return user;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
}

