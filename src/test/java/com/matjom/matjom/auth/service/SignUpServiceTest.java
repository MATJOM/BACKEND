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

    private static final String EMAIL = "user@example.com";                           // 테스트용 이메일.
    private static final String NAME = "User";                                        // 테스트용 이름.
    private static final String PASSWORD = "password123";                             // 입력 비밀번호.
    private static final String ENCODED_PASSWORD = "encoded-password";                // 암호화된 비밀번호.
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174001"); // 사용자 ID.

    @Mock
    private UserRepository userRepository;                                             // 사용자 저장소 모킹.

    @Mock
    private PasswordEncoder passwordEncoder;                                           // 비밀번호 인코더 모킹.

    @Mock
    private LoginService loginService;                                                 // 로그인 서비스 모킹.

    @InjectMocks
    private SignUpService signUpService;                                               // 테스트 대상 서비스.

    private SignUpRequest request;                                                     // 회원가입 요청 DTO.

    @BeforeEach
    void setUp() {
        request = new SignUpRequest();                                                 // 테스트에 사용할 요청 DTO를 만든다.
        request.setEmail(EMAIL);                                                       // 이메일을 입력한다.
        request.setPassword(PASSWORD);                                                 // 비밀번호를 입력한다.
        request.setName(NAME);                                                         // 이름을 입력한다.
    }

    // 신규 사용자를 등록하면 토큰을 발급한다.
    @Test
    void signUp_createsNewUser_andIssuesTokens() {
        // Given
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.empty()); // 동일 이메일 사용자가 없다고 응답한다.
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);          // 비밀번호 인코딩 결과를 제공한다.
        LoginResult expected = loginResult();                                         // 발급될 토큰 결과를 구성한다.
        when(loginService.issueTokens(any(User.class))).thenReturn(expected);         // 토큰 발급을 성공으로 모킹한다.

        // When
        LoginResult result = signUpService.signUp(request);                           // 회원가입 로직을 실행한다.

        // Then
        assertThat(result.getAccessToken()).isEqualTo(expected.getAccessToken());     // 액세스 토큰이 기대값이다.
        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);       // 저장된 사용자를 캡처한다.
        verify(userRepository).save(savedCaptor.capture());                            // 신규 사용자가 저장되었음을 검증한다.
        User saved = savedCaptor.getValue();                                           // 저장된 사용자 객체를 꺼낸다.
        assertThat(saved.getEmail()).isEqualTo(EMAIL);                                // 이메일이 요청과 일치한다.
        assertThat(saved.getName()).isEqualTo(NAME);                                  // 이름이 요청과 일치한다.
        assertThat(saved.getPassword()).isEqualTo(ENCODED_PASSWORD);                  // 비밀번호가 인코딩되어 저장된다.
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.LOCAL);                // 공급자가 LOCAL이다.
        ArgumentCaptor<User> tokenCaptor = ArgumentCaptor.forClass(User.class);       // 토큰 발급 시 전달된 사용자를 캡처한다.
        verify(loginService).issueTokens(tokenCaptor.capture());                      // 토큰 발급이 호출되었음을 검증한다.
        assertThat(tokenCaptor.getValue().getEmail()).isEqualTo(EMAIL);               // 토큰 발급 대상 이메일이 일치한다.
    }

    // 삭제된 사용자가 다시 가입하면 계정을 복구하고 토큰을 발급한다.
    @Test
    void signUp_restoresDeletedUser() {
        // Given
        User deletedUser = User.createLocalUser(EMAIL, "old", ENCODED_PASSWORD);     // 삭제된 사용자를 구성한다.
        setField(deletedUser, "id", USER_ID);                                        // 사용자 ID를 주입한다.
        deletedUser.markDeleted();                                                    // 삭제 상태로 표시한다.
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(deletedUser)); // 저장소가 삭제된 사용자를 반환한다.
        when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);          // 새 비밀번호 인코딩 값을 제공한다.
        when(loginService.issueTokens(deletedUser)).thenReturn(loginResult());        // 토큰 발급 결과를 제공한다.

        // When
        LoginResult result = signUpService.signUp(request);                           // 회원가입 로직을 실행한다.

        // Then
        assertThat(result.getAccessToken()).isNotBlank();                             // 새 액세스 토큰이 발급된다.
        assertThat(deletedUser.isDeleted()).isFalse();                                // 삭제 상태가 해제된다.
        verify(userRepository).save(deletedUser);                                     // 복구된 계정이 저장된다.
        verify(loginService).issueTokens(deletedUser);                                // 토큰 발급이 진행된다.
    }

    // 이미 존재하는 이메일로 가입하면 예외를 던진다.
    @Test
    void signUp_throwsWhenEmailAlreadyExists() {
        // Given
        User active = createUser(false);                                              // 이미 존재하는 사용자를 준비한다.
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.LOCAL)).thenReturn(Optional.of(active)); // 저장소가 활성 사용자를 반환한다.

        // When & Then
        assertThrows(AuthException.class, () -> signUpService.signUp(request));        // 예외가 발생해야 한다.

        // Then
        verify(userRepository, never()).save(any());                                   // 사용자 저장이 호출되지 않는다.
        verify(loginService, never()).issueTokens(any());                              // 토큰 발급도 호출되지 않는다.
    }

    private LoginResult loginResult() {
        LoginResponse response = LoginResponse.builder()                              // Helper: 로그인 응답을 구성한다.
                .email(EMAIL)
                .name(NAME)
                .provider(AuthProvider.LOCAL)
                .refreshToken("refresh")
                .build();
        return LoginResult.from("access", response);                                 // Helper: 토큰 결과를 반환한다.
    }

    private User createUser(boolean deleted) {
        User user = User.createLocalUser(EMAIL, NAME, ENCODED_PASSWORD);              // Helper: 사용자 엔티티를 생성한다.
        setField(user, "id", USER_ID);                                               // Helper: ID를 주입한다.
        if (deleted) {
            user.markDeleted();                                                      // Helper: 필요 시 삭제 상태로 표시한다.
        }
        return user;                                                                 // Helper: 사용자 엔티티를 반환한다.
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);             // Reflection: 필드를 가져온다.
            field.setAccessible(true);                                              // Reflection: 접근 가능하도록 한다.
            field.set(target, value);                                               // Reflection: 값 주입.
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);                                         // Reflection: 실패 시 런타임 예외를 던진다.
        }
    }
}