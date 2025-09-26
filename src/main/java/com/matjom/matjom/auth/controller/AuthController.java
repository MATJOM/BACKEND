package com.matjom.matjom.auth.controller;

import com.matjom.matjom.auth.dto.GoogleOAuthRequest;
import com.matjom.matjom.auth.dto.LoginRequest;
import com.matjom.matjom.auth.dto.LoginResponse;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.dto.ReissueRequest;
import com.matjom.matjom.auth.dto.ReissueResponse;
import com.matjom.matjom.auth.dto.ReissueResult;
import com.matjom.matjom.auth.dto.SignUpRequest;
import com.matjom.matjom.auth.service.GoogleOAuthService;
import com.matjom.matjom.auth.service.LoginService;
import com.matjom.matjom.auth.service.LogoutService;
import com.matjom.matjom.auth.service.ReissueService;
import com.matjom.matjom.auth.service.SignUpService;
import com.matjom.matjom.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final SignUpService signUpService;
    private final LoginService loginService;
    private final LogoutService logoutService;
    private final ReissueService reissueService;
    private final GoogleOAuthService googleOAuthService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<LoginResponse>> signup(@Valid @RequestBody SignUpRequest request) {
        LoginResult result = signUpService.signUp(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.getAccessToken())
                .body(ApiResponse.ok(result.getResponse()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.getAccessToken())
                .body(ApiResponse.ok(result.getResponse()));
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String header) {
        String accessToken = header.replaceFirst("Bearer ", "");
        logoutService.logout(accessToken);
    }

    @PostMapping("/oauth/google/callback")
    public ResponseEntity<ApiResponse<LoginResponse>> googleCallback(@Valid @RequestBody GoogleOAuthRequest request) {
        LoginResult result = googleOAuthService.signIn(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + result.getAccessToken())
                .body(ApiResponse.ok(result.getResponse()));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<ReissueResponse>> reissue(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String header,
            @RequestBody ReissueRequest request
    ) {
        String accessToken = header.replaceFirst("Bearer ", "");
        String refreshToken = request.getRefreshToken();
        ReissueResult result = reissueService.reissue(accessToken, refreshToken);
        String newAccessToken = result.getAccessToken();

        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken)
                .body(ApiResponse.ok(result.getResponse()));
    }
}
