package com.yanarchat.backend.user;

import java.util.UUID;

public class UserDto {

    public record UpdateUsernameRequest(String username) {}

    public record UserResponse(UUID id, String email, String username) {}
}
