package com.matjom.matjom.auth.controller;

import com.matjom.matjom.auth.dto.*;
import com.matjom.matjom.auth.service.LoginService;
import com.matjom.matjom.auth.service.LogoutService;
import com.matjom.matjom.auth.service.ReissueService;
import com.matjom.matjom.auth.service.SignUpService;
import com.matjom.matjom.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final SignUpService signUpService;
    private final LoginService loginService;
    private final LogoutService logoutService;
    private final ReissueService reissueService;

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

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<ReissueResponse>> reissue(@RequestHeader(HttpHeaders.AUTHORIZATION) String header,
                                                              @RequestBody ReissueRequest request) {
        String accessToken = header.replaceFirst("Bearer ", "");
        String refreshToken = request.getRefreshToken();
        ReissueResult result = reissueService.reissue(accessToken, refreshToken);
        String newAccessToken = result.getAccessToken();

        return ResponseEntity.ok()
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken)
                .body(ApiResponse.ok(result.getResponse()));
    }
}
