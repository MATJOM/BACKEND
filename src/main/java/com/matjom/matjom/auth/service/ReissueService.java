package com.matjom.matjom.auth.service;

import com.matjom.matjom.auth.dto.ReissueResponse;
import com.matjom.matjom.auth.dto.ReissueResult;
import com.matjom.matjom.auth.repository.RefreshTokenRepository;
import com.matjom.matjom.auth.repository.TokenBlacklistRepository;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.common.security.jwt.JwtTokenProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReissueService {

    private static final String REFRESH_TOKEN_TYPE = "REFRESH";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    public ReissueResult reissue(String accessToken, String refreshToken) {
        UUID userId = jwtTokenProvider.getUserId(accessToken);

        if (tokenBlacklistRepository.exists(accessToken)) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

        String storedRefreshToken = refreshTokenRepository.find(user.getId())
                .orElseThrow(() -> new AuthException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (!storedRefreshToken.equals(refreshToken)) {
            throw new AuthException(ErrorCode.INVALID_TOKEN);
        }

        if (!REFRESH_TOKEN_TYPE.equals(jwtTokenProvider.getTokenType(refreshToken))) {
            throw new AuthException(ErrorCode.INVALID_TOKEN);
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(user);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user);

        refreshTokenRepository.save(user.getId(), newRefreshToken);
        tokenBlacklistRepository.save(accessToken, jwtTokenProvider.getRemainingValidity(accessToken));

        return ReissueResult.from(newAccessToken, ReissueResponse.from(user, newRefreshToken));
    }
}

