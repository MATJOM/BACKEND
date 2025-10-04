package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.repository.RefreshTokenRepository;
import com.matjom.matjom.auth.repository.TokenBlacklistRepository;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
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

    private static final String ACCESS_TOKEN = "access-token";                       // Í∏∞Ï°¥ ?°ÏÑ∏???†ÌÅ∞.
    private static final String REFRESH_TOKEN = "refresh-token";                     // Í∏∞Ï°¥ Î¶¨ÌîÑ?àÏãú ?†ÌÅ∞.
    private static final String NEW_ACCESS_TOKEN = "new-access";                     // ?¨Î∞úÍ∏âÎêú ?°ÏÑ∏???†ÌÅ∞.
    private static final String NEW_REFRESH_TOKEN = "new-refresh";                   // ?¨Î∞úÍ∏âÎêú Î¶¨ÌîÑ?àÏãú ?†ÌÅ∞.
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426655440001");
    private static final Duration TTL = Duration.ofMinutes(5);

    @Mock
    private JwtTokenProvider jwtTokenProvider;                                        // ?†ÌÅ∞ ?ùÏÑ±Í∏?Î™®ÌÇπ.
    @Mock
    private UserRepository userRepository;                                            // ?¨Ïö©???Ä?•ÏÜå Î™®ÌÇπ.
    @Mock
    private RefreshTokenRepository refreshTokenRepository;                            // Î¶¨ÌîÑ?àÏãú ?†ÌÅ∞ ?Ä?•ÏÜå Î™®ÌÇπ.
    @Mock
    private TokenBlacklistRepository tokenBlacklistRepository;                        // Î∏îÎûôÎ¶¨Ïä§???Ä?•ÏÜå Î™®ÌÇπ.

    @InjectMocks
    private ReissueService reissueService;                                            // ?åÏä§???Ä???úÎπÑ??

    private User user;                                                                // Í≥µÌÜµ ?¨Ïö©???îÌã∞??

    @BeforeEach
    void setUp() {
        user = User.createLocalUser("user@example.com", "User", "encoded");        // Helper: ?¨Ïö©???îÌã∞?∞Î? ÎßåÎì†??
        setField(user, "id", USER_ID);
    }

    // ?ïÏÉÅ?ÅÏúºÎ°??¨Î∞úÍ∏âÎêòÎ©????†ÌÅ∞Í≥??¨Ïö©???ïÎ≥¥Í∞Ä Î∞òÌôò?úÎã§.
    @Test
    void reissue_success() {
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.find(USER_ID)).thenReturn(Optional.of(REFRESH_TOKEN));
        when(jwtTokenProvider.getTokenType(REFRESH_TOKEN)).thenReturn("REFRESH");
        when(jwtTokenProvider.createAccessToken(user)).thenReturn(NEW_ACCESS_TOKEN);
        when(jwtTokenProvider.createRefreshToken(user)).thenReturn(NEW_REFRESH_TOKEN);
        when(jwtTokenProvider.getRemainingValidity(ACCESS_TOKEN)).thenReturn(TTL);

        LoginResult result = reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN);

        assertThat(result.getAccessToken()).isEqualTo(NEW_ACCESS_TOKEN);               // ???°ÏÑ∏???†ÌÅ∞???¥Í∏¥??
        assertThat(result.getResponse().getRefreshToken()).isEqualTo(NEW_REFRESH_TOKEN);             // ??Î¶¨ÌîÑ?àÏãú ?†ÌÅ∞???¥Í∏¥??
        assertThat(result.getResponse().getName()).isEqualTo("User");                 // ?ëÎãµ ?¥Î¶Ñ???†Ï??úÎã§.
        verify(refreshTokenRepository).save(USER_ID, NEW_REFRESH_TOKEN);               // ??Î¶¨ÌîÑ?àÏãú ?†ÌÅ∞???Ä?•Îêú??
        verify(tokenBlacklistRepository).save(ACCESS_TOKEN, TTL);                     // Í∏∞Ï°¥ ?†ÌÅ∞?Ä Î∏îÎûôÎ¶¨Ïä§?∏Ïóê ?±Î°ù?úÎã§.
    }

    // ?Ä?•Îêú Î¶¨ÌîÑ?àÏãú ?†ÌÅ∞???§Î•¥Î©??àÏô∏Î•??òÏßÑ??
    @Test
    void reissue_throwsWhenRefreshTokenMismatch() {
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.find(USER_ID)).thenReturn(Optional.of("different"));

        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        verify(jwtTokenProvider, never()).createAccessToken(user);                    // ???†ÌÅ∞?Ä Î∞úÍ∏â?òÏ? ?äÎäî??
    }

    // ?¨Ïö©?êÎ? Ï∞æÏ? Î™ªÌïòÎ©??∏Ï¶ù ?§Ìå® ?àÏô∏Î•??òÏßÑ??
    @Test
    void reissue_throwsWhenUserNotFound() {
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(refreshTokenRepository, never()).find(USER_ID);                        // Î¶¨ÌîÑ?àÏãú ?†ÌÅ∞ Ï°∞ÌöåÍ∞Ä ?¥Î£®?¥Ï?ÏßÄ ?äÎäî??
    }

    // ?†ÌÅ∞ ?Ä?ÖÏù¥ REFRESH Í∞Ä ?ÑÎãàÎ©??àÏô∏Î•??òÏßÑ??
    @Test
    void reissue_throwsWhenTokenTypeIsNotRefresh() {
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);
        when(tokenBlacklistRepository.exists(ACCESS_TOKEN)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.find(USER_ID)).thenReturn(Optional.of(REFRESH_TOKEN));
        when(jwtTokenProvider.getTokenType(REFRESH_TOKEN)).thenReturn("ACCESS");

        assertThatThrownBy(() -> reissueService.reissue(ACCESS_TOKEN, REFRESH_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);

        verify(jwtTokenProvider, never()).createAccessToken(user);                    // ???†ÌÅ∞?Ä Î∞úÍ∏â?òÏ? ?äÎäî??
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
