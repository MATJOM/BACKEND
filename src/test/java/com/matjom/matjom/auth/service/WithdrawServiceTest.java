package com.matjom.matjom.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithdrawServiceTest {

    private static final String ACCESS_TOKEN = "access-token";
    private static final String EMAIL = "user@example.com";
    private static final AuthProvider PROVIDER = AuthProvider.GOOGLE;

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private LogoutService logoutService;

    @InjectMocks
    private WithdrawService withdrawService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.createOAuthUser(EMAIL, "User", null);
    }

    @Test
    void withdraw_marksDeletedAndLogsOut() {
        when(jwtTokenProvider.getEmailFromToken(ACCESS_TOKEN)).thenReturn(EMAIL);
        when(jwtTokenProvider.getProviderFromToken(ACCESS_TOKEN)).thenReturn(PROVIDER.name());
        when(userRepository.findByEmailAndProvider(EMAIL, PROVIDER)).thenReturn(Optional.of(user));

        withdrawService.withdraw(ACCESS_TOKEN);

        assertThat(user.isDeleted()).isTrue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(logoutService).logoutHelper(userCaptor.capture(), org.mockito.ArgumentMatchers.eq(ACCESS_TOKEN));
        assertThat(userCaptor.getValue()).isSameAs(user);
    }

    @Test
    void withdraw_throwsWhenAlreadyDeleted() {
        user.markDeleted();
        when(jwtTokenProvider.getEmailFromToken(ACCESS_TOKEN)).thenReturn(EMAIL);
        when(jwtTokenProvider.getProviderFromToken(ACCESS_TOKEN)).thenReturn(PROVIDER.name());
        when(userRepository.findByEmailAndProvider(EMAIL, PROVIDER)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> withdrawService.withdraw(ACCESS_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAW_ALREADY_INACTIVE);

        verify(logoutService, never()).logoutHelper(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void withdraw_throwsWhenUserNotFound() {
        when(jwtTokenProvider.getEmailFromToken(ACCESS_TOKEN)).thenReturn(EMAIL);
        when(jwtTokenProvider.getProviderFromToken(ACCESS_TOKEN)).thenReturn(PROVIDER.name());
        when(userRepository.findByEmailAndProvider(EMAIL, PROVIDER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> withdrawService.withdraw(ACCESS_TOKEN))
                .isInstanceOf(AuthException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verify(logoutService, never()).logoutHelper(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
}
