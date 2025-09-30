package com.matjom.matjom.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.auth.service.LogoutService;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.dto.ChangePasswordRequest;
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
class ChangePasswordServiceTest {

    private static final String ACCESS_TOKEN = "access-token";                     // 테스트용 액세스 토큰.
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426655440010"); // 사용자 ID.
    private static final String EMAIL = "user@example.com";                        // 사용자 이메일.
    private static final String NAME = "User";                                     // 사용자 이름.
    private static final String CURRENT_PASSWORD = "currentPass123";               // 현재 비밀번호.
    private static final String ENCODED_CURRENT = "encoded-current";               // 암호화된 현재 비밀번호.
    private static final String NEW_PASSWORD = "newPass456";                       // 새 비밀번호.
    private static final String ENCODED_NEW = "encoded-new";                       // 암호화된 새 비밀번호.

    @Mock
    private JwtTokenProvider jwtTokenProvider;                                      // JWT 파서 모킹.
    @Mock
    private UserRepository userRepository;                                          // 사용자 저장소 모킹.
    @Mock
    private PasswordEncoder passwordEncoder;                                        // 비밀번호 인코더 모킹.
    @Mock
    private LogoutService logoutService;                                            // 로그아웃 서비스 모킹.

    @InjectMocks
    private ChangePasswordService changePasswordService;                            // 테스트 대상 서비스.

    private User user;                                                              // 테스트용 사용자 엔티티.

    @BeforeEach
    void setUp() {
        user = User.createLocalUser(EMAIL, NAME, ENCODED_CURRENT);                  // 테스트에 활용할 로컬 계정을 생성한다.
        setField(user, "id", USER_ID);                                             // 사용자 ID를 주입한다.
    }

    // 현재 비밀번호가 일치하면 새 비밀번호로 변경하고 로그아웃을 수행한다.
    @Test
    void changePassword_updatesPasswordAndLogsOut() {
        // Given
        ChangePasswordRequest request = buildRequest(CURRENT_PASSWORD, NEW_PASSWORD); // 요청 DTO를 구성한다.
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);           // 토큰에서 사용자 ID를 추출한다.
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));         // 사용자 조회가 성공한다.
        when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_CURRENT)).thenReturn(true); // 현재 비밀번호가 일치한다.
        when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(ENCODED_NEW);           // 새 비밀번호 인코딩 값을 제공한다.

        // When
        changePasswordService.changePassword(ACCESS_TOKEN, request);                  // 비밀번호 변경을 수행한다.

        // Then
        assertThat(user.getPassword()).isEqualTo(ENCODED_NEW);                       // 사용자 비밀번호가 새 값으로 변경된다.
        verify(userRepository).save(user);                                           // 변경된 사용자가 저장된다.
        verify(logoutService).logoutHelper(USER_ID, ACCESS_TOKEN);                   // 기존 토큰 정리를 위해 로그아웃 헬퍼가 호출된다.
    }

    // 현재 비밀번호가 일치하지 않으면 예외를 던진다.
    @Test
    void changePassword_throwsWhenCurrentPasswordMismatch() {
        // Given
        ChangePasswordRequest request = buildRequest("wrong", NEW_PASSWORD);        // 잘못된 현재 비밀번호를 담는다.
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);           // 토큰에서 사용자 ID를 추출한다.
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));         // 사용자 조회가 성공한다.
        when(passwordEncoder.matches("wrong", ENCODED_CURRENT)).thenReturn(false);   // 현재 비밀번호 검증이 실패한다.

        // When & Then
        assertThatThrownBy(() -> changePasswordService.changePassword(ACCESS_TOKEN, request)) // 예외가 발생해야 한다.
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);

        // Then
        verify(userRepository, never()).save(user);                                   // 비밀번호가 변경되지 않으므로 저장되지 않는다.
        verify(logoutService, never()).logoutHelper(USER_ID, ACCESS_TOKEN);           // 로그아웃 헬퍼도 호출되지 않는다.
    }

    // 사용자를 찾지 못하면 자격 증명 오류를 던진다.
    @Test
    void changePassword_throwsWhenUserNotFound() {
        // Given
        ChangePasswordRequest request = buildRequest(CURRENT_PASSWORD, NEW_PASSWORD); // 정상 요청을 준비한다.
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);           // 토큰에서 사용자 ID를 추출한다.
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());          // 사용자 조회가 실패하도록 한다.

        // When & Then
        assertThatThrownBy(() -> changePasswordService.changePassword(ACCESS_TOKEN, request)) // 예외가 발생해야 한다.
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        // Then
        verify(passwordEncoder, never()).matches(CURRENT_PASSWORD, ENCODED_CURRENT);  // 사용자를 찾지 못했으므로 비밀번호 검증이 수행되지 않는다.
    }

    // OAuth 계정은 비밀번호 변경을 지원하지 않는다.
    @Test
    void changePassword_throwsWhenUserIsNotLocal() {
        // Given
        ChangePasswordRequest request = buildRequest(CURRENT_PASSWORD, NEW_PASSWORD); // 정상 요청을 준비한다.
        User oauthUser = User.createOAuthUser(EMAIL, NAME, null);                     // OAuth 사용자를 생성한다.
        setField(oauthUser, "id", USER_ID);                                          // 사용자 ID를 주입한다.
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);           // 토큰에서 사용자 ID를 추출한다.
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(oauthUser));    // OAuth 사용자를 반환한다.

        // When & Then
        assertThatThrownBy(() -> changePasswordService.changePassword(ACCESS_TOKEN, request)) // 예외가 발생해야 한다.
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        // Then
        verify(passwordEncoder, never()).matches(CURRENT_PASSWORD, ENCODED_CURRENT);  // OAuth 계정에서는 비밀번호 검증이 수행되지 않는다.
    }

    private ChangePasswordRequest buildRequest(String current, String next) {
        ChangePasswordRequest request = new ChangePasswordRequest();                  // Helper: 요청 DTO를 생성한다.
        request.setCurrentPassword(current);                                          // Helper: 현재 비밀번호를 설정한다.
        request.setNewPassword(next);                                                 // Helper: 새 비밀번호를 설정한다.
        return request;                                                               // Helper: DTO를 반환한다.
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);              // Reflection: 필드를 가져온다.
            field.setAccessible(true);                                                // Reflection: 접근 가능하도록 한다.
            field.set(target, value);                                                 // Reflection: 값 주입.
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            throw new RuntimeException(ex);                                          // Reflection: 실패 시 런타임 예외를 던진다.
        }
    }
}