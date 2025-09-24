package com.matjom.matjom.auth.service;

import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.dto.SignUpRequest;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignUpService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginService loginService;

    @Transactional
    public LoginResult signUp(SignUpRequest request) {
        Optional<User> optionalUser = userRepository.findByEmailAndProvider(request.getEmail(), AuthProvider.LOCAL);
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User user;
        if (optionalUser.isPresent()) {
            User existing = optionalUser.get();
            if (existing.getDeletedAt() == null) {
                throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            existing.restore();
            existing.changePassword(encodedPassword);
            user = existing;
            userRepository.save(user);
        } else {
            user = User.createLocalUser(request.getEmail(), request.getName(), encodedPassword);
            userRepository.save(user);
        }

        return loginService.issueTokens(user);
    }
}
