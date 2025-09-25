package com.matjom.matjom.auth.service;

import com.matjom.matjom.auth.repository.RefreshTokenRepository;
import com.matjom.matjom.auth.repository.TokenBlacklistRepository;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutService {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    public void logout(String accessToken) {
        String email = jwtTokenProvider.getEmailFromToken(accessToken);
        String provider = jwtTokenProvider.getProviderFromToken(accessToken);

        User user = userRepository.findByEmailAndProvider(email, AuthProvider.valueOf(provider))
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

        refreshTokenRepository.delete(user.getId());

        Duration ttl = jwtTokenProvider.getRemainingValidity(accessToken);
        tokenBlacklistRepository.save(accessToken, ttl);
    }
}
