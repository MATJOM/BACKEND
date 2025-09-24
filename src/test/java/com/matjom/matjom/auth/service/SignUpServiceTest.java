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
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
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

    private static final String EMAIL = "user@example.com";
    private static final String NAME = "User";
    private static final String PASSWORD = "password123";
    private static final String ENCODED_PASSWORD = "encoded-password";
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private LoginService loginService;

    @InjectMocks
    private SignUpService signUpService;

    private SignUpRequest request;

    @BeforeEach
    void setUp() {
        request = new SignUpRequest();
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);
        request.setName(NAME);
    }

    @Test
    void signUp_createsNewUser_andIssuesTokens() {
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
        LoginResult expected = loginResult();
        when(loginService.issueTokens(any(User.class))).thenReturn(expected);

        LoginResult result = signUpService.signUp(request);

        assertThat(result.getAccessToken()).isEqualTo(expected.getAccessToken());
        assertThat(result.getRefreshToken()).isEqualTo(expected.getRefreshToken());

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedCaptor.capture());
        User saved = savedCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getName()).isEqualTo(NAME);
        assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD);
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.LOCAL);

        ArgumentCaptor<User> tokenCaptor = ArgumentCaptor.forClass(User.class);
        verify(loginService).issueTokens(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue()).isSameAs(saved);
    }

    @Test
    void signUp_existingActiveUser_throwsConflict() {
        User existing = createPersistedUser(false);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);

        AuthException exception = assertThrows(AuthException.class, () -> signUpService.signUp(request));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(loginService, never()).issueTokens(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void signUp_restoresSoftDeletedUser_andIssuesTokens() {
        User deletedUser = createPersistedUser(true);
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(deletedUser));
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
        LoginResult expected = loginResult();
        when(loginService.issueTokens(any(User.class))).thenReturn(expected);

        LoginResult result = signUpService.signUp(request);

        assertThat(result.getAccessToken()).isEqualTo(expected.getAccessToken());
        assertThat(deletedUser.isDeleted()).isFalse();
        assertThat(deletedUser.getPassword()).isEqualTo(ENCODED_PASSWORD);

        verify(userRepository).save(deletedUser);
        verify(loginService).issueTokens(deletedUser);
    }

    private User createPersistedUser(boolean deleted) {
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

    private LoginResult loginResult() {
        LoginResponse response = LoginResponse.builder()
                .email(EMAIL)
                .name(NAME)
                .provider(AuthProvider.LOCAL)
                .build();
        return LoginResult.from("access", "refresh", response);
    }
}
