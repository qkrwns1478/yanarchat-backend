package com.yanarchat.backend.conversation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ConversationDto {

    public record CreateRequest(UUID characterId, String title) {}

    public record SendMessageRequest(String content) {}

    public record ConversationResponse(
            UUID id,
            UUID characterId,
            String characterName,
            String title,
            LocalDateTime createdAt
    ) {}

    public record ConversationSummary(
            UUID id,
            UUID characterId,
            String characterName,
            String title,
            LocalDateTime updatedAt
    ) {}

    public record ConversationPageResponse(
            List<ConversationSummary> content,
            long totalElements,
            int totalPages
    ) {}

    public record MessageResponse(
            UUID id,
            String role,
            String content,
            LocalDateTime createdAt
    ) {}

    public record ConversationDetail(
            UUID id,
            UUID characterId,
            String characterName,
            String title,
            List<MessageResponse> messages
    ) {}

    record ChatContext(
            UUID conversationId,
            UUID characterId,
            UUID userId,
            String systemPrompt,
            String speechStyle,
            String personality,
            List<Map<String, String>> recentMessages,
            List<String> longTermMemories,
            String lastUserMessage
    ) {}
}
