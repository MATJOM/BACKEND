package com.matjom.matjom.user.service;

import com.matjom.matjom.auth.service.LogoutService;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WithdrawService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final LogoutService logoutService;

    @Transactional
    public void withdraw(String accessToken) {
        UUID userId = jwtTokenProvider.getUserId(accessToken);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

        if (user.isDeleted()) {
            throw new AuthException(ErrorCode.WITHDRAW_ALREADY_INACTIVE);
        }

        user.markDeleted();
        userRepository.save(user);

        logoutService.logoutHelper(userId, accessToken);
    }
}