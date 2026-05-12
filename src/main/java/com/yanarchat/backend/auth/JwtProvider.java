package com.yanarchat.backend.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secretString;

    private SecretKey secretKey;
    private static final long ACCESS_TOKEN_EXPIRE_TIME = 1000L * 60 * 30; // 30분
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 1000L * 60 * 60 * 24 * 7; // 7일

    @PostConstruct
    protected void init() {
        // JJWT 0.12.x 방식: byte 배열로 SecretKey 객체 생성
        this.secretKey = Keys.hmacShaKeyFor(secretString.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(String userId) {
        return createToken(userId, ACCESS_TOKEN_EXPIRE_TIME);
    }

    public String createRefreshToken(String userId) {
        return createToken(userId, REFRESH_TOKEN_EXPIRE_TIME);
    }

    private String createToken(String userId, long expireTime) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireTime))
                .signWith(secretKey)
                .compact();
    }

    public String getUserId(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}