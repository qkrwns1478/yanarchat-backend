package com.yanarchat.backend.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;

    @Test
    @DisplayName("회원가입 통합 테스트 - 회원가입 성공")
    void signup_integration_test() throws Exception {
        AuthDto.SignupRequest request = new AuthDto.SignupRequest("newuser@example.com", "newbie", "password123!");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("로그인 통합 테스트 - 성공 시 200 OK와 토큰 반환")
    void login_success_integration_test() throws Exception {
        // given: 사전에 회원가입을 진행하여 DB에 유저를 만들어 둡니다.
        AuthDto.SignupRequest signupRequest = new AuthDto.SignupRequest("loginuser@example.com", "loginUser", "password123!");
        authService.signup(signupRequest);

        // when & then: 가입한 정보로 로그인 요청
        AuthDto.LoginRequest loginRequest = new AuthDto.LoginRequest("loginuser@example.com", "password123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    @DisplayName("로그인 통합 테스트 - 없는 계정 401 반환")
    void login_with_invalid_credentials_returns_401() throws Exception {
        AuthDto.LoginRequest request = new AuthDto.LoginRequest("nonexistent@example.com", "wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}