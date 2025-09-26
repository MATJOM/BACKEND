package com.matjom.matjom.auth.service;

import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WithdrawService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final LogoutService logoutService;

    public void withdraw(String accessToken){
        String email = jwtTokenProvider.getEmailFromToken(accessToken);
        String provider = jwtTokenProvider.getProviderFromToken(accessToken);

        User user = userRepository.findByEmailAndProvider(email, AuthProvider.valueOf(provider))
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

        if (user.isDeleted()){
            throw new AuthException(ErrorCode.WITHDRAW_ALREADY_INACTIVE);
        }

        user.markDeleted();
        logoutService.logoutHelper(user, accessToken);
    }
}
