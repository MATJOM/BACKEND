package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.repository.RefreshTokenRepository;
import com.matjom.matjom.auth.repository.TokenBlacklistRepository;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String EMAIL = "user@example.com";
    private static final AuthProvider PROVIDER = AuthProvider.LOCAL;
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426655440000");

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;

    @InjectMocks
    private LogoutService logoutService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.createLocalUser(EMAIL, "name", "password");
        setField(user, "id", USER_ID);
    }

    @Test
    void logout_deletesRefreshTokenAndBlacklistsAccessToken() {
        when(jwtTokenProvider.getEmailFromToken(ACCESS_TOKEN)).thenReturn(EMAIL);
        when(jwtTokenProvider.getProviderFromToken(ACCESS_TOKEN)).thenReturn(PROVIDER.name());
        when(jwtTokenProvider.getRemainingValidity(ACCESS_TOKEN)).thenReturn(Duration.ofMinutes(5));
        when(userRepository.findByEmailAndProvider(EMAIL, PROVIDER)).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> logoutService.logout(ACCESS_TOKEN));

        verify(refreshTokenRepository).delete(USER_ID);
        verify(tokenBlacklistRepository).save(ACCESS_TOKEN, Duration.ofMinutes(5));
    }

    @Test
    void logout_throwsWhenUserNotFound() {
        when(jwtTokenProvider.getEmailFromToken(ACCESS_TOKEN)).thenReturn(EMAIL);
        when(jwtTokenProvider.getProviderFromToken(ACCESS_TOKEN)).thenReturn(PROVIDER.name());
        when(userRepository.findByEmailAndProvider(EMAIL, PROVIDER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> logoutService.logout(ACCESS_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
