package com.matjom.matjom.auth.controller;

import com.matjom.matjom.auth.dto.LoginRequest;
import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.SignUpRequest;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.service.LoginService;
import com.matjom.matjom.auth.service.SignUpService;
import com.matjom.matjom.auth.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final SignUpService signUpService;
    private final LoginService loginService;

    @PostMapping("/signup")
    public void signup(@Valid @RequestBody SignUpRequest request) {
        signUpService.signUp(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResult result = loginService.login(request);

        //Authorization 헤더에 AccessToken 담기
        response.setHeader("Authorization", "Bearer " + result.getAccessToken());

        //HttpOnly 쿠키에 RefreshToken 담기
        response.addCookie(CookieUtil.createRefreshTokenCookie(result.getRefreshToken()));

        return result.getResponse();
    }
}
