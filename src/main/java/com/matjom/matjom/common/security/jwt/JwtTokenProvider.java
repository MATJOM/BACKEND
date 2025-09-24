package com.matjom.matjom.common.security.jwt;

import com.matjom.matjom.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component // Spring Bean 등록
public class JwtTokenProvider {
    //default-unsafe-secret을 폴백 값으로 사용. 환경변수로 jwt.secret 값 넣어줘야함
    @Value("${jwt.secret:default-unsafe-secret}")
    private String secretKey;

    private static final long ACCESS_TOKEN_VALIDITY = 15 * 60 * 1000L;        // 15분
    private static final long REFRESH_TOKEN_VALIDITY = 14 * 24 * 60 * 60 * 1000L; // 14일
    private SecretKey key; // HMAC-SHA 서명용 키

    @PostConstruct
    protected void init() {
        // 문자열 secretKey → SecretKey 객체로 변환
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // AccessToken 생성
    public String createAccessToken(User user) {
        return createToken(user, ACCESS_TOKEN_VALIDITY);
    }

    // RefreshToken 생성
    public String createRefreshToken(User user) {
        return createToken(user, REFRESH_TOKEN_VALIDITY);
    }

    // 공통 토큰 생성 메서드
    private String createToken(User user, long validityInMillis) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityInMillis);

        return Jwts.builder()
                .subject(user.getEmail())                 // sub = email
                .claim("name", user.getName())            // name claim
                .claim("provider", user.getProvider().name()) // provider claim
                .issuedAt(now)                            // 발급 시각
                .expiration(expiry)                       // 만료 시각
                .signWith(key)                            // 키로 서명
                .compact();                               // 최종 JWT 문자열
    }

    // Claims 파싱
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key) // 서명 검증
                .build()
                .parseSignedClaims(token)
                .getPayload();   // Payload(Claims) 반환
    }

    // 토큰 유효성 검사
    public boolean validateToken(String token) {
        try {
            parseClaims(token); // 파싱 과정에서 만료/위조 검사됨
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getRefreshTokenValidity() {
        return REFRESH_TOKEN_VALIDITY;
    }
}