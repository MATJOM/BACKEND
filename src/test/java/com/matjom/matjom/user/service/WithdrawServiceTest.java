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
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceTest {

    private static final String ACCESS_TOKEN = "access-token";                      // 테스트용 액세스 토큰.
    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426655440001"); // 사용자 ID.

    @Mock
    private UserRepository userRepository;                                           // 사용자 저장소 모킹.
    @Mock
    private JwtTokenProvider jwtTokenProvider;                                       // 토큰 파서 모킹.
    @Mock
    private LogoutService logoutService;                                             // 로그아웃 헬퍼 모킹.

    @InjectMocks
    private WithdrawService withdrawService;                                         // 테스트 대상 서비스.

    private User user;                                                               // 테스트용 사용자 엔티티.

    @BeforeEach
    void setUp() {
        user = User.createLocalUser("user@example.com", "User", "password");      // 테스트에 사용할 로컬 계정을 생성한다.
    }

    // 탈퇴에 성공하면 계정이 삭제 처리되고 로그아웃이 진행된다.
    @Test
    void withdraw_marksDeletedAndLogsOut() {
        // Given
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);           // 토큰에서 사용자 ID를 추출한다.
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));         // 사용자 조회가 성공한다.

        // When
        withdrawService.withdraw(ACCESS_TOKEN);                                       // 탈퇴 로직을 실행한다.

        // Then
        assertThat(user.isDeleted()).isTrue();                                       // 사용자 상태가 삭제됨으로 표시된다.
        verify(logoutService).logoutHelper(USER_ID, ACCESS_TOKEN);                   // 로그아웃 헬퍼가 호출된다.
    }

    // 이미 삭제된 계정을 다시 탈퇴하려 하면 예외를 던진다.
    @Test
    void withdraw_throwsWhenAlreadyDeleted() {
        // Given
        user.markDeleted();                                                           // 사용자를 삭제 상태로 만든다.
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);           // 토큰에서 사용자 ID를 추출한다.
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));         // 삭제된 사용자를 반환한다.

        // When & Then
        assertThatThrownBy(() -> withdrawService.withdraw(ACCESS_TOKEN))              // 예외가 발생해야 한다.
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAW_ALREADY_INACTIVE);

        // Then
        verify(logoutService, never()).logoutHelper(USER_ID, ACCESS_TOKEN);           // 추가 로그아웃 처리는 호출되지 않는다.
    }

    // 사용자를 찾지 못하면 자격 증명 오류를 던진다.
    @Test
    void withdraw_throwsWhenUserNotFound() {
        // Given
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(USER_ID);           // 토큰에서 사용자 ID를 추출한다.
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());          // 사용자 조회가 실패하도록 한다.

        // When & Then
        assertThatThrownBy(() -> withdrawService.withdraw(ACCESS_TOKEN))              // 예외가 발생해야 한다.
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        // Then
        verify(logoutService, never()).logoutHelper(USER_ID, ACCESS_TOKEN);           // 로그아웃 헬퍼가 호출되지 않는다.
    }
}