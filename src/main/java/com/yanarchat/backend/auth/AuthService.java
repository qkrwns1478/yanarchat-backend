package com.yanarchat.backend.auth;

import com.yanarchat.backend.user.User;
import com.yanarchat.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;

    private static final long REFRESH_TOKEN_TTL = 7 * 24 * 60 * 60 * 1000L; // 7일

    @Transactional
    public AuthDto.TokenResponse signup(AuthDto.SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
                .email(request.email())
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .build();

        User savedUser = userRepository.save(user);

        return issueTokens(savedUser.getId().toString());
    }

    @Transactional(readOnly = true)
    public AuthDto.TokenResponse login(AuthDto.LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        return issueTokens(user.getId().toString());
    }

    @Transactional(readOnly = true)
    public AuthDto.TokenResponse refresh(AuthDto.RefreshRequest request) {
        String refreshToken = request.refreshToken();

        // 1. 토큰 자체의 서명 및 만료일 검증
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh 토큰입니다.");
        }

        String userId = jwtProvider.getUserId(refreshToken);

        // 2. Redis에 저장된 토큰과 비교 (탈취 방지 및 로그아웃 유저 검증)
        String savedToken = redisTemplate.opsForValue().get("RT:" + userId);
        if (savedToken == null || !savedToken.equals(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh 토큰이 일치하지 않거나 만료되었습니다.");
        }

        // 3. 검증 통과 시 새로운 Access/Refresh 토큰 발급
        return issueTokens(userId);
    }

    public void logout(String userId) {
        // Redis에서 Refresh 토큰을 삭제하여 로그아웃 처리
        redisTemplate.delete("RT:" + userId);
    }

    // 중복되는 토큰 발급 및 Redis 저장 로직 모듈화
    private AuthDto.TokenResponse issueTokens(String userId) {
        String accessToken = jwtProvider.createAccessToken(userId);
        String refreshToken = jwtProvider.createRefreshToken(userId);

        redisTemplate.opsForValue().set(
                "RT:" + userId,
                refreshToken,
                Duration.ofMillis(REFRESH_TOKEN_TTL)
        );

        return new AuthDto.TokenResponse(accessToken, refreshToken);
    }
}