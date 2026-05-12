package com.yanarchat.backend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/me")
    public ResponseEntity<UserDto.UserResponse> updateUsername(
            @AuthenticationPrincipal String userId,
            @RequestBody UserDto.UpdateUsernameRequest request) {
        return ResponseEntity.ok(userService.updateUsername(userId, request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteUser(@AuthenticationPrincipal String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
