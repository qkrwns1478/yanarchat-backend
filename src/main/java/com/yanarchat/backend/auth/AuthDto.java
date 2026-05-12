package com.yanarchat.backend.auth;

public class AuthDto {
    public record SignupRequest(
            String email,
            String username,
            String password
    ) {}

    public record LoginRequest(
            String email,
            String password
    ) {}

    public record TokenResponse(
            String accessToken,
            String refreshToken
    ) {}

    public record RefreshRequest(
            String refreshToken
    ) {}
}