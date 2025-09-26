package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.dto.GoogleOAuthRequest;
import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.common.security.oauth.GoogleOAuthClient;
import com.matjom.matjom.common.security.oauth.GoogleOAuthProfile;
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

    private static final String ID_TOKEN = "id-token";
    private static final String EMAIL = "user@example.com";
    private static final String NAME = "User";

    @Mock
    private GoogleOAuthClient googleOAuthClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LoginService loginService;

    @InjectMocks
    private GoogleOAuthService googleOAuthService;

    private GoogleOAuthProfile profile;
    private GoogleOAuthRequest request;
    private User existingUser;
    private LoginResult loginResult;

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

        existingUser = User.createOAuthUser(EMAIL, "Old Name", null);
        setId(existingUser, UUID.randomUUID());

        loginResult = LoginResult.from(
                "access",
                LoginResponse.builder()
                        .email(EMAIL)
                        .name(NAME)
                        .provider(AuthProvider.GOOGLE)
                        .refreshToken("refresh")
                        .build()
        );
    }

    @Test
    void signIn_registersNewUserWhenNotExists() {
        when(googleOAuthClient.verify(ID_TOKEN)).thenReturn(profile);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.GOOGLE)).thenReturn(Optional.empty());
        when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            setId(saved, UUID.randomUUID());
            return saved;
        });
        when(loginService.issueTokens(Mockito.any(User.class))).thenReturn(loginResult);

        LoginResult result = googleOAuthService.signIn(request);

        assertThat(result.getAccessToken()).isEqualTo("access");
        assertThat(result.getResponse().getEmail()).isEqualTo(EMAIL);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(NAME);
        assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
    }

    @Test
    void signIn_restoresDeletedUser() {
        existingUser.markDeleted();
        when(googleOAuthClient.verify(ID_TOKEN)).thenReturn(profile);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.GOOGLE)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);
        when(loginService.issueTokens(Mockito.any(User.class))).thenReturn(loginResult);

        LoginResult result = googleOAuthService.signIn(request);

        assertThat(result.getAccessToken()).isEqualTo("access");

        ArgumentCaptor<User> loginCaptor = ArgumentCaptor.forClass(User.class);
        verify(loginService).issueTokens(loginCaptor.capture());
        User restored = loginCaptor.getValue();
        assertThat(restored.isDeleted()).isFalse();
        assertThat(restored.getName()).isEqualTo(NAME);
        assertThat(restored.getEmail()).isEqualTo(EMAIL);

        verify(userRepository).save(existingUser);
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
