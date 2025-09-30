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
import java.lang.reflect.Field;
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
class ReissueServiceTest {

    private static final String ACCESS_TOKEN = "access-token";                   // 湲곗〈 ?≪꽭???좏겙.
    private static final String REFRESH_TOKEN = "refresh-token";                 // 湲곗〈 由ы봽?덉떆 ?좏겙.
    private static final String EMAIL = "user@example.com";                      // ?ъ슜???대찓??
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426655440001");
    private static final Duration TTL = Duration.ofMinutes(5);

    @Mock
    private JwtTokenProvider jwtTokenProvider;                                    // ?좏겙 ?뚯꽌/諛쒓툒湲?紐⑦궧.
    @Mock
    private UserRepository userRepository;                                        // ?ъ슜????μ냼 紐⑦궧.
    @Mock
    private RefreshTokenRepository refreshTokenRepository;                        // 由ы봽?덉떆 ?좏겙 ??μ냼 紐⑦궧.
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;                    // 釉붾옓由ъ뒪????μ냼 紐⑦궧.

    @InjectMocks
    private ReissueService reissueService;                                        // ?뚯뒪??????쒕퉬??

    private User user;                                                            // 怨듯넻 ?ъ슜???뷀떚??

    @BeforeEach
    void setUp() {
        user = User.createLocalUser(EMAIL, "name", "encoded");                 // ?뚯뒪?몄슜 ?ъ슜???뷀떚?곕? ?앹꽦?쒕떎.
        setField(user, "id", USER_ID);                                           // ?ъ슜??ID瑜?二쇱엯?쒕떎.
    }

    // ?뺤긽?곸쑝濡??щ컻湲됲븯硫????≪꽭??由ы봽?덉떆 ?좏겙????ν븯怨?湲곗〈 ?≪꽭???좏겙??釉붾옓由ъ뒪?몄뿉 湲곕줉?쒕떎.
    @Test
    void reissue_success() {
        // Given
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.find(USER_ID)).thenReturn(Optional.of(REFRESH_TOKEN));
        when(jwtTokenProvider.getTokenType(REFRESH_TOKEN)).thenReturn("REFRESH");
        when(jwtTokenProvider.createAccessToken(user)).thenReturn("new-access");
        when(jwtTokenProvider.createRefreshToken(user)).thenReturn("new-refresh");
        when(jwtTokenProvider.getRemainingValidity(ACCESS_TOKEN)).thenReturn(TTL);

        // When
        ReissueResult result = reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN);

        // Then
        assertThat(result.getAccessToken()).isEqualTo("new-access");              // ???≪꽭???좏겙??諛섑솚?쒕떎.
        assertThat(result.getResponse().getEmail()).isEqualTo(EMAIL);              // ?묐떟 ?대찓?쇱씠 ?쇱튂?쒕떎.
        assertThat(result.getResponse().getProvider()).isEqualTo(AuthProvider.LOCAL); // ?묐떟 怨듦툒?먭? LOCAL?대떎.
        assertThat(result.getResponse().getRefreshToken()).isEqualTo("new-refresh");           // 응답에 새 리프레시 토큰이 포함된다.
        verify(refreshTokenRepository).save(USER_ID, "new-refresh");              // ??由ы봽?덉떆 ?좏겙????λ맂??
        verify(tokenBlacklistRepository).save(ACCESS_TOKEN, TTL);                 // 湲곗〈 ?≪꽭???좏겙??釉붾옓由ъ뒪?몄뿉 湲곕줉?쒕떎.
    }

    // ??λ맂 由ы봽?덉떆 ?좏겙怨??쇱튂?섏? ?딆쑝硫?INVALID_TOKEN ?덉쇅瑜??섏쭊??
    @Test
    void reissue_throwsWhenRefreshTokenMismatch() {
        // Given
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.find(USER_ID)).thenReturn(Optional.of("different-token"));

        // When & Then
        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        // Then
        verify(jwtTokenProvider, never()).createAccessToken(user);                 // ???좏겙??諛쒓툒?섏? ?딅뒗??
    }

    // ?ъ슜?먮? 李얠? 紐삵븯硫?INVALID_CREDENTIALS ?덉쇅瑜??섏쭊??
    @Test
    void reissue_throwsWhenUserNotFound() {
        // Given
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        // Then
        verify(refreshTokenRepository, never()).find(USER_ID);                     // 由ы봽?덉떆 ?좏겙 議고쉶媛 諛쒖깮?섏? ?딅뒗??
    }

    // 由ы봽?덉떆 ?좏겙????낆씠 REFRESH媛 ?꾨땲硫?INVALID_TOKEN ?덉쇅瑜??섏쭊??
    @Test
    void reissue_throwsWhenTokenTypeIsNotRefresh() {
        // Given
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.find(USER_ID)).thenReturn(Optional.of(REFRESH_TOKEN));
        when(jwtTokenProvider.getTokenType(REFRESH_TOKEN)).thenReturn("ACCESS");

        // When & Then
        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        // Then
        verify(jwtTokenProvider, never()).createAccessToken(user);
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


