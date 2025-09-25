package com.matjom.matjom.common.security.jwt;

import com.matjom.matjom.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtTokenProvider {
    @Value("")
    private String secretKey;

    private static final long ACCESS_TOKEN_VALIDITY = 15 * 60 * 1000L;
    private static final long REFRESH_TOKEN_VALIDITY = 14 * 24 * 60 * 60 * 1000L;
    private SecretKey key;

    @PostConstruct
    protected void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(User user) {
        return createToken(user, ACCESS_TOKEN_VALIDITY);
    }

    public String createRefreshToken(User user) {
        return createToken(user, REFRESH_TOKEN_VALIDITY);
    }

    private String createToken(User user, long validityInMillis) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityInMillis);

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("name", user.getName())
                .claim("provider", user.getProvider().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Long getRefreshTokenValidity() {
        return REFRESH_TOKEN_VALIDITY;
    }

    public Claims getClaimsEvenIfExpired(String token) {
        try {
            return parseClaims(token);
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public Duration getRemainingValidity(String token) {
        Date expiration = getClaimsEvenIfExpired(token).getExpiration();
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    public String getEmailFromToken(String token) {
        return getClaimsEvenIfExpired(token).getSubject();
    }

    public String getProviderFromToken(String token) {
        return getClaimsEvenIfExpired(token).get("provider", String.class);
    }
}
