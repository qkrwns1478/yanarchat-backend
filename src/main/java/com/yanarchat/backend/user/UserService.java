package com.yanarchat.backend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public UserDto.UserResponse updateUsername(String userId, UserDto.UpdateUsernameRequest request) {
        User user = findUser(userId);
        user.updateUsername(request.username());
        return toResponse(user);
    }

    @Transactional
    public void deleteUser(String userId) {
        User user = findUser(userId);
        user.markAsDeleted();
        redisTemplate.delete("RT:" + userId);
    }

    private User findUser(String userId) {
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private UserDto.UserResponse toResponse(User user) {
        return new UserDto.UserResponse(user.getId(), user.getEmail(), user.getUsername());
    }
}
