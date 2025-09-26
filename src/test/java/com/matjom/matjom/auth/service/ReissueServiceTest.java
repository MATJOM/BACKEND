package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.dto.ReissueResult;
import com.matjom.matjom.auth.repository.RefreshTokenRepository;
import com.matjom.matjom.auth.repository.TokenBlacklistRepository;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReissueServiceTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String EMAIL = "user@example.com";
    private static final AuthProvider PROVIDER = AuthProvider.LOCAL;
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426655440001");

    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;

    @InjectMocks
    private ReissueService reissueService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.createLocalUser(EMAIL, "name", "encoded");
        setField(user, "id", USER_ID);
    }

    @Test
    void reissue_success() {
        Claims claims = mockClaims();
        when(jwtTokenProvider.getClaimsEvenIfExpired(ACCESS_TOKEN)).thenReturn(claims);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findByEmailAndProvider(EMAIL, PROVIDER)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.find(USER_ID)).thenReturn(Optional.of(REFRESH_TOKEN));
        when(jwtTokenProvider.createAccessToken(user)).thenReturn("new-access");

        ReissueResult result = reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN);

        assertThat(result.getAccessToken()).isEqualTo("new-access");
        verify(refreshTokenRepository).find(USER_ID);
        verify(jwtTokenProvider).createAccessToken(user);
    }

    @Test
    void reissue_failsWhenBlacklisted() {
        Claims claims = mockClaims();
        when(jwtTokenProvider.getClaimsEvenIfExpired(ACCESS_TOKEN)).thenReturn(claims);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(true);

        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(refreshTokenRepository, never()).find(any());
    }

    @Test
    void reissue_failsWhenUserMissing() {
        Claims claims = mockClaims();
        when(jwtTokenProvider.getClaimsEvenIfExpired(ACCESS_TOKEN)).thenReturn(claims);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findByEmailAndProvider(EMAIL, PROVIDER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void reissue_failsWhenRefreshTokenMissing() {
        Claims claims = mockClaims();
        when(jwtTokenProvider.getClaimsEvenIfExpired(ACCESS_TOKEN)).thenReturn(claims);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findByEmailAndProvider(EMAIL, PROVIDER)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.find(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    void reissue_failsWhenRefreshTokenMismatch() {
        Claims claims = mockClaims();
        when(jwtTokenProvider.getClaimsEvenIfExpired(ACCESS_TOKEN)).thenReturn(claims);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findByEmailAndProvider(EMAIL, PROVIDER)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.find(USER_ID)).thenReturn(Optional.of("another-token"));

        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    private Claims mockClaims() {
        Claims claims = Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn(EMAIL);
        when(claims.get("provider", String.class)).thenReturn(PROVIDER.name());
        return claims;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
