package com.yanarchat.backend.memory;

import java.time.LocalDateTime;
import java.util.UUID;

public class MemoryDto {

    public record MemoryResponse(
            UUID id,
            String content,
            String memoryType,
            LocalDateTime createdAt
    ) {}
}
