package com.yanarchat.backend.auth;

import com.yanarchat.backend.user.User;
import com.yanarchat.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks private AuthService authService;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("회원가입 - 성공")
    void signup_success() {
        // given
        AuthDto.SignupRequest request = new AuthDto.SignupRequest("test@test.com", "tester", "password");
        given(userRepository.existsByEmail(anyString())).willReturn(false);
        given(passwordEncoder.encode(anyString())).willReturn("hashed-password");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        User user = User.builder().email(request.email()).username(request.username()).password("hashed-password").build();

        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        given(userRepository.save(any(User.class))).willReturn(user);

        // when
        AuthDto.TokenResponse response = authService.signup(request);

        // then
        assertNotNull(response);
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("로그인 - 성공 시 토큰 발급 및 Redis 저장")
    void login_success() {
        // given
        AuthDto.LoginRequest request = new AuthDto.LoginRequest("test@test.com", "password");
        User user = User.builder().email("test@test.com").username("tester").password("hashed-password").build();

        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        // Mocking: DB 조회 성공, 비밀번호 일치, 토큰 발급, Redis 동작
        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
        given(jwtProvider.createAccessToken(anyString())).willReturn("mock-access-token");
        given(jwtProvider.createRefreshToken(anyString())).willReturn("mock-refresh-token");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        AuthDto.TokenResponse response = authService.login(request);

        // then
        assertNotNull(response);
        assertEquals("mock-access-token", response.accessToken());
        assertEquals("mock-refresh-token", response.refreshToken());

        // Redis의 set 메서드가 1번 호출되었는지 검증 (토큰 저장 로직 확인)
        verify(valueOperations, times(1)).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("로그인 - 비밀번호 불일치 시 실패")
    void login_fail_password() {
        // given
        AuthDto.LoginRequest request = new AuthDto.LoginRequest("test@test.com", "wrong-password");
        User user = User.builder().email("test@test.com").password("hashed-password").build();

        given(userRepository.findByEmail(anyString())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        // when & then
        assertThrows(ResponseStatusException.class, () -> authService.login(request));
    }
}