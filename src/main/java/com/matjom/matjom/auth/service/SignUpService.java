package com.matjom.matjom.auth.service;

import com.matjom.matjom.auth.dto.SignUpRequest;
import com.matjom.matjom.common.exception.base.AuthException;
import com.matjom.matjom.common.exception.message.ErrorCode;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SignUpService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signUp(SignUpRequest request) {

        Optional<User> optionalUser = userRepository.findByEmailAndProvider(request.getEmail(), AuthProvider.LOCAL);
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        if (optionalUser.isPresent()) {
            if (optionalUser.get().getDeletedAt() == null){
                throw new AuthException(ErrorCode.EMAIL_ALREADY_EXISTS);
            } else {
                User user = optionalUser.get();
                user.restore();
                user.changePassword(encodedPassword);
                userRepository.save(user);
            }
        } else {
            User user = User.createLocalUser(request.getEmail(), request.getName(), encodedPassword);
            userRepository.save(user);
        }
    }
}
