package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
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

    private static final String ID_TOKEN = "id-token";                     // 테스트용 ID 토큰.
    private static final String EMAIL = "user@example.com";                // 사용자 이메일.
    private static final String NAME = "User";                             // 사용자 이름.

    @Mock
    private GoogleOAuthClient googleOAuthClient;                            // Google 토큰 검증 클라이언트.
    @Mock
    private UserRepository userRepository;                                  // 사용자 저장소 모킹.
    @Mock
    private LoginService loginService;                                      // 로그인 서비스 모킹.

    @InjectMocks
    private GoogleOAuthService googleOAuthService;                          // 테스트 대상 서비스.

    private GoogleOAuthProfile profile;                                     // 검증 후 반환될 프로필.
    private GoogleOAuthRequest request;                                     // 요청 DTO.
    private User existingUser;                                             // 저장소에서 반환할 사용자.
    private LoginResult loginResult;                                      // 토큰 발급 결과.

    @BeforeEach
    void setUp() {
        profile = GoogleOAuthProfile.builder()                             // 테스트에서 사용할 Google 프로필을 구성한다.
                .email(EMAIL)
                .name(NAME)
                .subject("sub")
                .picture("pic")
                .build();

        request = new GoogleOAuthRequest();                                // 요청 DTO를 생성한다.
        request.setIdToken(ID_TOKEN);                                      // ID 토큰을 지정한다.

        existingUser = User.createOAuthUser(EMAIL, "Old Name", null);     // 기존 OAuth 사용자를 만든다.
        setId(existingUser, UUID.randomUUID());                            // 사용자 ID를 주입한다.

        loginResult = LoginResult.from(                                    // 토큰 발급 결과를 미리 구성한다.
                "access",
                LoginResponse.builder()
                        .email(EMAIL)
                        .name(NAME)
                        .provider(AuthProvider.GOOGLE)
                        .refreshToken("refresh")
                        .build()
        );
    }

    // 신규 Google 사용자를 등록하면 토큰을 발급한다.
    @Test
    void signIn_registersNewUserWhenNotExists() {
        // Given
        when(googleOAuthClient.verify(ID_TOKEN)).thenReturn(profile);                              // Google 토큰 검증이 성공하도록 모킹한다.
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.GOOGLE)).thenReturn(Optional.empty()); // 동일 계정이 없다고 응답한다.
        when(userRepository.save(Mockito.any(User.class))).thenAnswer(invocation -> {             // 저장 시 ID를 부여해 반환한다.
            User saved = invocation.getArgument(0);
            setId(saved, UUID.randomUUID());
            return saved;
        });
        when(loginService.issueTokens(Mockito.any(User.class))).thenReturn(loginResult);           // 토큰 발급 결과를 제공한다.

        // When
        LoginResult result = googleOAuthService.signIn(request);                                   // OAuth 로그인 흐름을 실행한다.

        // Then
        assertThat(result.getAccessToken()).isEqualTo("access");                                  // 액세스 토큰이 기대값이다.
        assertThat(result.getResponse().getEmail()).isEqualTo(EMAIL);                              // 응답 이메일이 일치한다.
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);                         // 저장된 사용자를 캡처한다.
        verify(userRepository).save(captor.capture());                                             // 신규 사용자가 저장되었음을 검증한다.
        assertThat(captor.getValue().getName()).isEqualTo(NAME);                                   // 저장된 이름이 최신 값이다.
        assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);                // 공급자가 GOOGLE이다.
    }

    // 삭제된 사용자가 다시 로그인하면 계정을 복구하고 토큰을 발급한다.
    @Test
    void signIn_restoresDeletedUser() {
        // Given
        existingUser.markDeleted();                                                                // 사용자를 삭제 상태로 표시한다.
        when(googleOAuthClient.verify(ID_TOKEN)).thenReturn(profile);                              // Google 토큰 검증이 성공한다.
        when(userRepository.findByEmailAndProvider(EMAIL, AuthProvider.GOOGLE)).thenReturn(Optional.of(existingUser)); // 저장소가 기존 사용자를 반환한다.
        when(userRepository.save(existingUser)).thenReturn(existingUser);                          // 저장 결과로 동일 사용자 객체를 반환한다.
        when(loginService.issueTokens(Mockito.any(User.class))).thenReturn(loginResult);           // 토큰 발급 결과를 제공한다.

        // When
        LoginResult result = googleOAuthService.signIn(request);                                   // OAuth 로그인 흐름을 실행한다.

        // Then
        assertThat(result.getAccessToken()).isEqualTo("access");                                  // 액세스 토큰이 기대값이다.
        ArgumentCaptor<User> loginCaptor = ArgumentCaptor.forClass(User.class);                   // 토큰 발급 시 전달된 사용자를 캡처한다.
        verify(loginService).issueTokens(loginCaptor.capture());                                   // 복구된 사용자로 토큰이 발급되었는지 검증한다.
        User restored = loginCaptor.getValue();                                                    // 복구된 사용자 정보를 꺼낸다.
        assertThat(restored.isDeleted()).isFalse();                                                // 삭제 상태가 해제되었다.
        assertThat(restored.getName()).isEqualTo(NAME);                                            // 이름이 최신 값으로 변경되었다.
        assertThat(restored.getEmail()).isEqualTo(EMAIL);                                          // 이메일은 동일하게 유지된다.
        verify(userRepository).save(existingUser);                                                 // 복구된 계정이 저장소에 반영된다.
    }

    private void setId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");                                        // Reflection: id 필드에 접근한다.
            field.setAccessible(true);                                                             // Reflection: 접근 가능하도록 설정한다.
            field.set(user, id);                                                                   // Reflection: ID 값을 주입한다.
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);                                                         // Reflection: 실패 시 런타임 예외를 던진다.
        }
    }
}