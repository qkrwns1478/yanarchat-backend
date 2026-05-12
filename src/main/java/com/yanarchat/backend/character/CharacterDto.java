package com.yanarchat.backend.character;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class CharacterDto {

    public record CreateRequest(String name, String personaDescription) {}

    public record UpdateRequest(String name, String personaDescription) {}

    public record PersonaAttributes(
            String speechStyle,
            String personality,
            List<String> traits,
            String background,
            String systemPrompt
    ) {}

    public record FileInfo(UUID id, String originalName, String fileType) {}

    public record CharacterResponse(
            UUID id,
            String name,
            String personaDescription,
            String speechStyle,
            String personality,
            List<String> traits,
            String background,
            String systemPrompt,
            String avatarUrl,
            List<FileInfo> files,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    record CharacterCacheEntry(java.util.UUID ownerId, CharacterResponse response) {}

    public record CharacterSummary(
            UUID id,
            String name,
            String personality,
            String avatarUrl,
            LocalDateTime createdAt
    ) {}
}
