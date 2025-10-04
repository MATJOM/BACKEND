package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.dto.GoogleOAuthRequest;
import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.oauth.GoogleOAuthClient;
import com.matjom.matjom.auth.oauth.GoogleOAuthProfile;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    private static final String ID_TOKEN = "id-token";                                // ?ŒìŠ¤?¸ìš© ID ? í°.
    private static final String EMAIL = "user@example.com";                           // ?ŒìŠ¤?¸ìš© ?´ë©”??
    private static final String NAME = "User";                                        // ?ŒìŠ¤?¸ìš© ?´ë¦„.

    @Mock
    private GoogleOAuthClient googleOAuthClient;                                      // Google ? í° ê²€ì¦ê¸° ëª¨í‚¹.
    @Mock
    private UserRepository userRepository;                                            // ?¬ìš©???€?¥ì†Œ ëª¨í‚¹.
    @Mock
    private LoginService loginService;                                                // ë¡œê·¸???œë¹„??ëª¨í‚¹.

    @InjectMocks
    private GoogleOAuthService googleOAuthService;                                    // ?ŒìŠ¤???€???œë¹„??

    private GoogleOAuthProfile profile;                                               // ê²€ì¦?ê²°ê³¼ ?„ë¡œ??
    private GoogleOAuthRequest request;                                               // OAuth ?”ì²­ DTO.
    private LoginResult loginResult;                                                  // ? í° ë°œê¸‰ ê²°ê³¼.

    @BeforeEach
    void setUp() {
        profile = GoogleOAuthProfile.builder()
                .email(EMAIL)
                .name(NAME)
                .subject("sub")
                .picture("pic")
                .build();

        request = new GoogleOAuthRequest();
        request.setIdToken(ID_TOKEN);

        LoginResponse expectedResponse = LoginResponse.builder()
                .name(NAME)
                .refreshToken("refresh-token")
                .build();
        loginResult = LoginResult.from(
                "access-token",
                expectedResponse
        );
    }

    // ? ê·œ Google ê³„ì •?´ë©´ ?¬ìš©???ˆì½”?œë? ?ì„±?˜ê³  ? í°??ë°œê¸‰?œë‹¤.
    @Test
    void signIn_registersNewUserWhenNotExists() {
        // Given
        when(googleOAuthClient.verify(ID_TOKEN)).thenReturn(profile);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.GOOGLE)).thenReturn(Optional.empty());
        when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            setId(saved, UUID.randomUUID());
            return saved;
        });
        when(loginService.issueTokens(Mockito.any(User.class))).thenReturn(loginResult);

        // When
        LoginResult result = googleOAuthService.signIn(request);

        // Then
        assertThat(result.getAccessToken()).isEqualTo("access-token");                 // ?¡ì„¸??? í°??ë°˜í™˜?œë‹¤.
        assertThat(result.getResponse().getRefreshToken()).isEqualTo("refresh-token");               // ë¦¬í”„?ˆì‹œ ? í°???¨ê»˜ ë°˜í™˜?œë‹¤.
        assertThat(result.getResponse().getName()).isEqualTo(NAME);                     // ?‘ë‹µ???´ë¦„???¬í•¨?œë‹¤.

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);              // ?€?¥ëœ ?¬ìš©???•ë³´ë¥?ê²€ì¦í•œ??
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);                                  // ?´ë©”?¼ì´ ?„ë¡œ?„ê³¼ ?™ì¼?˜ë‹¤.
        assertThat(saved.getName()).isEqualTo(NAME);                                    // ?´ë¦„???„ë¡œ?„ê³¼ ?™ì¼?˜ë‹¤.
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.GOOGLE);                // ê³µê¸‰?ê? GOOGLE ë¡??€?¥ëœ??
        verify(loginService).issueTokens(saved);                                       // ? ê·œ ?¬ìš©?ë¡œ ? í° ë°œê¸‰???´ë£¨?´ì§„??
    }

    // ê¸°ì¡´ ê³„ì •???ˆìœ¼ë©?ê·¸ë?ë¡??¬ìš©?˜ê³  ì¶”ê? ?€?¥ì? ?˜ì? ?ŠëŠ”??
    @Test
    void signIn_reusesExistingUser() {
        // Given
        User existingUser = User.createOAuthUser(EMAIL, "Old Name", null);
        setId(existingUser, UUID.randomUUID());
        when(googleOAuthClient.verify(ID_TOKEN)).thenReturn(profile);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.GOOGLE)).thenReturn(Optional.of(existingUser));
        when(loginService.issueTokens(existingUser)).thenReturn(loginResult);

        // When
        LoginResult result = googleOAuthService.signIn(request);

        // Then
        assertThat(result.getAccessToken()).isEqualTo("access-token");                 // ?¡ì„¸??? í°???„ë‹¬?œë‹¤.
        assertThat(result.getResponse().getRefreshToken()).isEqualTo("refresh-token");               // ë¦¬í”„?ˆì‹œ ? í°???„ë‹¬?œë‹¤.
        assertThat(result.getResponse().getName()).isEqualTo(NAME);                     // ?‘ë‹µ ?´ë¦„?€ ? í° ê²°ê³¼ ê¸°ì??´ë‹¤.
        verify(userRepository, never()).save(Mockito.any());                            // ì¶”ê? ?€?¥ì´ ë°œìƒ?˜ì? ?ŠëŠ”??
        verify(loginService).issueTokens(existingUser);                                 // ê¸°ì¡´ ?¬ìš©?ë¡œ ? í° ë°œê¸‰???¸ì¶œ?œë‹¤.
    }

    private void setId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

