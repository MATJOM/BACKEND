package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    private static final String EMAIL = "user@example.com";                          // ?ŒìŠ¤?¸ìš© ?´ë©”??
    private static final String NAME = "User";                                       // ?ŒìŠ¤?¸ìš© ?´ë¦„.
    private static final String RAW_PASSWORD = "password123";                        // ?…ë ¥ ë¹„ë?ë²ˆí˜¸.
    private static final String ENCODED_PASSWORD = "encoded-password";               // ?”í˜¸?”ëœ ë¹„ë?ë²ˆí˜¸.
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private UserRepository userRepository;                                            // ?¬ìš©???€?¥ì†Œ ëª¨í‚¹.

    @Mock
    private PasswordEncoder passwordEncoder;                                          // ë¹„ë?ë²ˆí˜¸ ?¸ì½”??ëª¨í‚¹.

    @Mock
    private JwtTokenProvider jwtTokenProvider;                                        // ? í° ?ì„±ê¸?ëª¨í‚¹.

    @Mock
    private RefreshTokenRepository refreshTokenRepository;                            // ë¦¬í”„?ˆì‹œ ? í° ?€?¥ì†Œ ëª¨í‚¹.

    @Mock
    private LoginRateLimiter loginRateLimiter;                                        // ë¡œê·¸???œë„ ?œí•œê¸?ëª¨í‚¹.

    @InjectMocks
    private LoginService loginService;                                                // ?ŒìŠ¤???€???œë¹„??

    private User activeUser;                                                          // ?•ìƒ ?¬ìš©???”í‹°??

    @BeforeEach
    void setUp() {
        activeUser = createUser();                                                    // ë¡œê·¸?¸ì— ?¬ìš©???¬ìš©?ë? ë§Œë“ ??
    }

    // ë¡œê·¸?¸ì— ?±ê³µ?˜ë©´ ??? í°??ë°œê¸‰?˜ê³  ?•ë³´ë¥?ë°˜í™˜?œë‹¤.
    @Test
    void login_success() {
        LoginRequest request = createRequest();
        when(loginRateLimiter.isLimitReached(EMAIL)).thenReturn(false);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(activeUser)).thenReturn(ACCESS_TOKEN);
        when(jwtTokenProvider.createRefreshToken(activeUser)).thenReturn(REFRESH_TOKEN);

        LoginResult result = loginService.login(request);

        assertThat(result.getAccessToken()).isEqualTo(ACCESS_TOKEN);                  // ?¡ì„¸??? í°??ë°˜í™˜?œë‹¤.
        assertThat(result.getResponse().getRefreshToken()).isEqualTo(REFRESH_TOKEN);                // ë¦¬í”„?ˆì‹œ ? í°???¨ê»˜ ë°˜í™˜?œë‹¤.
        assertThat(result.getResponse().getName()).isEqualTo(NAME);                  // ?‘ë‹µ ë³¸ë¬¸???¬ìš©???´ë¦„???´ê¸´??
        verify(passwordEncoder).matches(RAW_PASSWORD, ENCODED_PASSWORD);             // ë¹„ë?ë²ˆí˜¸ ê²€ì¦ì´ ?˜í–‰?œë‹¤.
        verify(refreshTokenRepository).save(USER_ID, REFRESH_TOKEN);                  // ë¦¬í”„?ˆì‹œ ? í°???€?¥ëœ??
        verify(loginRateLimiter).reset(EMAIL);                                       // ?¤íŒ¨ ?Ÿìˆ˜ê°€ ì´ˆê¸°?”ëœ??
        verify(loginRateLimiter).isLimitReached(EMAIL);                              // ì°¨ë‹¨ ?¬ë?ë¥?ì¡°íšŒ?œë‹¤.
    }

    // ?´ë? ì°¨ë‹¨??ê²½ìš° ì¦‰ì‹œ ?ˆì™¸ë¥??˜ì§„??
    @Test
    void login_blocked_whenLimitAlreadyReached() {
        LoginRequest request = createRequest();
        when(loginRateLimiter.isLimitReached(EMAIL)).thenReturn(true);
        when(loginRateLimiter.getRemainingSeconds(EMAIL)).thenReturn(180L);

        AuthException exception = assertThrows(AuthException.class, () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOGIN_TOO_MANY_ATTEMPTS); // ì°¨ë‹¨ ?ˆì™¸ ì½”ë“œê°€ ë°˜í™˜?œë‹¤.
        assertThat(exception.getMessage()).contains("180");                             // ë©”ì‹œì§€???¨ì? ?œê°„???¬í•¨?œë‹¤.
        verify(loginRateLimiter).isLimitReached(EMAIL);                               // ì°¨ë‹¨ ?íƒœë¥?ì¡°íšŒ?œë‹¤.
        verify(loginRateLimiter).getRemainingSeconds(EMAIL);                          // ?¨ì? ?€ê¸??œê°„??ì¡°íšŒ?œë‹¤.
        verifyNoInteractions(userRepository);                                         // ?¬ìš©??ì¡°íšŒ???´ë£¨?´ì?ì§€ ?ŠëŠ”??
        verify(loginRateLimiter, never()).recordFailure(EMAIL);                       // ?¤íŒ¨ ?Ÿìˆ˜??ì¦ê??˜ì? ?ŠëŠ”??
        verify(loginRateLimiter, never()).reset(EMAIL);                               // ì´ˆê¸°?”ë„ ?˜í–‰?˜ì? ?ŠëŠ”??
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(refreshTokenRepository);
    }

    // ?¬ìš©?ê? ì¡´ì¬?˜ì? ?Šìœ¼ë©??¤íŒ¨ë¥?ê¸°ë¡?˜ê³  ?ˆì™¸ë¥??˜ì§„??
    @Test
    void login_userNotFound_recordsFailure() {
        LoginRequest request = createRequest();
        when(loginRateLimiter.isLimitReached(EMAIL)).thenReturn(false, false);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.empty());

        AuthException exception = assertThrows(AuthException.class, () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS); // ?¸ì¦ ?¤íŒ¨ ì½”ë“œê°€ ë°˜í™˜?œë‹¤.
        verify(loginRateLimiter).recordFailure(EMAIL);                                // ?¤íŒ¨ ?Ÿìˆ˜ë¥?ê¸°ë¡?œë‹¤.
        verify(loginRateLimiter, times(2)).isLimitReached(EMAIL);                     // ì°¨ë‹¨ ?¬ë?ë¥???ë²??•ì¸?œë‹¤.
        verify(loginRateLimiter, never()).getRemainingSeconds(EMAIL);                 // ?¨ì? ?œê°„??ì¡°íšŒ?˜ì? ?ŠëŠ”??
        verify(loginRateLimiter, never()).reset(EMAIL);                               // ì´ˆê¸°?”ë„ ?˜í–‰?˜ì? ?ŠëŠ”??
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtTokenProvider);
        verifyNoInteractions(refreshTokenRepository);
    }

    // ë¹„ë?ë²ˆí˜¸ê°€ ?€ë¦¬ë©´ ?¤íŒ¨ë¥?ê¸°ë¡?œë‹¤.
    @Test
    void login_invalidPassword_recordsFailure() {
        LoginRequest request = createRequest();
        when(loginRateLimiter.isLimitReached(EMAIL)).thenReturn(false, false);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class, () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS); // ?¸ì¦ ?¤íŒ¨ ì½”ë“œê°€ ë°˜í™˜?œë‹¤.
        verify(passwordEncoder).matches(RAW_PASSWORD, ENCODED_PASSWORD);              // ë¹„ë?ë²ˆí˜¸ ê²€ì¦ì´ ?œë„?œë‹¤.
        verify(loginRateLimiter).recordFailure(EMAIL);                                // ?¤íŒ¨ ?Ÿìˆ˜ê°€ ì¦ê??œë‹¤.
        verify(loginRateLimiter, times(2)).isLimitReached(EMAIL);                     // ì°¨ë‹¨ ?¬ë?ë¥???ë²??•ì¸?œë‹¤.
        verify(loginRateLimiter, never()).getRemainingSeconds(EMAIL);                 // ?¨ì? ?œê°„?€ ì¡°íšŒ?˜ì? ?ŠëŠ”??
        verify(loginRateLimiter, never()).reset(EMAIL);                               // ì´ˆê¸°?”ë„ ?˜í–‰?˜ì? ?ŠëŠ”??
        verifyNoInteractions(refreshTokenRepository);
    }

    // ë¹„ë?ë²ˆí˜¸ê°€ ë°˜ë³µ?´ì„œ ?€ë¦¬ë©´ ì°¨ë‹¨ ?ˆì™¸ë¥??˜ì§„??
    @Test
    void login_invalidPassword_triggersLimitExceededResponse() {
        LoginRequest request = createRequest();
        when(loginRateLimiter.isLimitReached(EMAIL)).thenReturn(false, true);
        when(loginRateLimiter.getRemainingSeconds(EMAIL)).thenReturn(120L);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        AuthException exception = assertThrows(AuthException.class, () -> loginService.login(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOGIN_TOO_MANY_ATTEMPTS); // ì°¨ë‹¨ ì½”ë“œê°€ ë°˜í™˜?œë‹¤.
        assertThat(exception.getMessage()).contains("120");                             // ë©”ì‹œì§€???¨ì? ?œê°„???¬í•¨?œë‹¤.
        verify(passwordEncoder).matches(RAW_PASSWORD, ENCODED_PASSWORD);              // ë¹„ë?ë²ˆí˜¸ ê²€ì¦ì´ ?œë„?œë‹¤.
        verify(loginRateLimiter).recordFailure(EMAIL);                                // ?¤íŒ¨ ?Ÿìˆ˜ë¥?ê¸°ë¡?œë‹¤.
        verify(loginRateLimiter, times(2)).isLimitReached(EMAIL);                     // ì°¨ë‹¨ ?¬ë?ë¥???ë²??•ì¸?œë‹¤.
        verify(loginRateLimiter).getRemainingSeconds(EMAIL);                          // ?¨ì? ?œê°„??ì¡°íšŒ?œë‹¤.
        verify(loginRateLimiter, never()).reset(EMAIL);                               // ì´ˆê¸°?”ëŠ” ?˜í–‰?˜ì? ?ŠëŠ”??
        verifyNoInteractions(refreshTokenRepository);
    }

    private LoginRequest createRequest() {
        LoginRequest request = new LoginRequest();                                    // Helper: ë¡œê·¸???”ì²­???ì„±?œë‹¤.
        request.setEmail(EMAIL);
        request.setPassword(RAW_PASSWORD);
        return request;
    }

    private User createUser() {
        User user = User.createLocalUser(EMAIL, NAME, ENCODED_PASSWORD);              // Helper: ?¬ìš©???”í‹°?°ë? ?ì„±?œë‹¤.
        setField(user, "id", USER_ID);
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
