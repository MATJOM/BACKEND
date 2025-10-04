package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.dto.SignUpRequest;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class SignUpServiceTest {

    private static final String EMAIL = "user@example.com";                           // ?ŒìŠ¤?¸ìš© ?´ë©”??
    private static final String NAME = "User";                                        // ?ŒìŠ¤?¸ìš© ?´ë¦„.
    private static final String PASSWORD = "password123";                             // ?…ë ¥ ë¹„ë?ë²ˆí˜¸.
    private static final String ENCODED_PASSWORD = "encoded-password";                // ?”í˜¸?”ëœ ë¹„ë?ë²ˆí˜¸.

    @Mock
    private UserRepository userRepository;                                             // ?¬ìš©???€?¥ì†Œ ëª¨í‚¹.

    @Mock
    private PasswordEncoder passwordEncoder;                                           // ë¹„ë?ë²ˆí˜¸ ?¸ì½”??ëª¨í‚¹.

    @Mock
    private LoginService loginService;                                                 // ë¡œê·¸???œë¹„??ëª¨í‚¹.

    @InjectMocks
    private SignUpService signUpService;                                               // ?ŒìŠ¤???€???œë¹„??

    private SignUpRequest request;                                                     // ?Œì›ê°€???”ì²­ DTO.

    @BeforeEach
    void setUp() {
        request = new SignUpRequest();                                                 // ?ŒìŠ¤?¸ì— ?¬ìš©???”ì²­???ì„±?œë‹¤.
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);
        request.setName(NAME);
    }

    // ? ê·œ ?Œì›?´ë©´ ê³„ì •???ì„±?˜ê³  ? í°??ë°œê¸‰?œë‹¤.
    @Test
    void signUp_createsLocalUser_andIssuesTokens() {
        // Given
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
        LoginResponse expectedResponse = LoginResponse.builder()
                .name(NAME)
                .refreshToken("refresh-token")
                .build();
        LoginResult expected = LoginResult.from(
                "access-token",
                expectedResponse
        );
        when(loginService.issueTokens(any(User.class))).thenReturn(expected);

        // When
        LoginResult result = signUpService.signUp(request);

        // Then
        assertThat(result.getAccessToken()).isEqualTo("access-token");                 // ?¡ì„¸??? í°??ê·¸ë?ë¡?ë°˜í™˜?œë‹¤.
        assertThat(result.getResponse().getRefreshToken()).isEqualTo("refresh-token");               // ë¦¬í”„?ˆì‹œ ? í°???¨ê»˜ ë°˜í™˜?œë‹¤.
        assertThat(result.getResponse().getName()).isEqualTo(NAME);                     // ?‘ë‹µ??ê°€?…ìê°€ ?œì‹œ?œë‹¤.

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);              // ?€?¥ëœ ?¬ìš©???•ë³´ë¥?ê²€ì¦í•œ??
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);                                  // ?€?¥ëœ ?´ë©”?¼ì´ ?¼ì¹˜?œë‹¤.
        assertThat(saved.getName()).isEqualTo(NAME);                                    // ?€?¥ëœ ?´ë¦„???¼ì¹˜?œë‹¤.
        assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD);                    // ë¹„ë?ë²ˆí˜¸ê°€ ?”í˜¸?”ëœ ê°’ìœ¼ë¡??€?¥ëœ??
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.LOCAL);                  // ê³µê¸‰?ê? LOCAL ë¡?ì§€?•ëœ??

        verify(passwordEncoder).encode(PASSWORD);                                      // ë¹„ë?ë²ˆí˜¸ê°€ ?¸ì½”?©ëœ??
        verify(loginService).issueTokens(saved);                                       // ? í° ë°œê¸‰???¸ì¶œ?œë‹¤.
    }

    // ?™ì¼???´ë©”?¼ì´ ?´ë? ì¡´ì¬?˜ë©´ ?ˆì™¸ë¥??˜ì§„??
    @Test
    void signUp_throwsWhenEmailAlreadyExists() {
        // Given
        User existing = User.createLocalUser(EMAIL, NAME, ENCODED_PASSWORD);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(java.util.Optional.of(existing));

        // When & Then
        assertThrows(AuthException.class, () -> signUpService.signUp(request));         // ?ˆì™¸ê°€ ë°œìƒ?´ì•¼ ?œë‹¤.

        // Then
        verify(userRepository, never()).save(any());                                    // ?¬ìš©???€?¥ì? ?¼ì–´?˜ì? ?ŠëŠ”??
        verify(loginService, never()).issueTokens(any());                               // ? í° ë°œê¸‰???¸ì¶œ?˜ì? ?ŠëŠ”??
    }
}

