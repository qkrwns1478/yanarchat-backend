package com.yanarchat.backend.conversation;

import com.yanarchat.backend.common.LmStudioService;
import com.yanarchat.backend.memory.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationService conversationService;
    private final LmStudioService lmStudioService;
    private final MemoryService memoryService;
    private final ObjectMapper objectMapper;

    private static final long SSE_TIMEOUT_MS = 120_000L;

    public SseEmitter chat(String userId, UUID conversationId, String content) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        ConversationDto.ChatContext ctx;
        try {
            ctx = conversationService.prepareChat(userId, conversationId, content);
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        String systemPrompt = buildSystemPrompt(ctx);
        StringBuilder fullResponse = new StringBuilder();

        lmStudioService.streamChat(systemPrompt, ctx.recentMessages())
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        token -> {
                            fullResponse.append(token);
                            try {
                                String json = objectMapper.writeValueAsString(Map.of("token", token));
                                emitter.send(SseEmitter.event().data(json));
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        },
                        error -> {
                            try {
                                emitter.send(SseEmitter.event().data("{\"error\": \"LM Studio 연결에 실패했습니다.\"}"));
                                emitter.complete();
                            } catch (Exception ignored) {
                                emitter.completeWithError(error);
                            }
                        },
                        () -> {
                            try {
                                String response = fullResponse.toString();
                                UUID messageId = conversationService.saveAssistantMessage(ctx.conversationId(), response);

                                final String userMsg = ctx.lastUserMessage();
                                final UUID characterId = ctx.characterId();
                                final UUID userUUID = ctx.userId();
                                CompletableFuture.runAsync(() -> {
                                    try {
                                        memoryService.extractAndSave(characterId, userUUID, userMsg, response);
                                    } catch (Exception ignored) {}
                                });

                                String doneJson = objectMapper.writeValueAsString(
                                        Map.of("done", true, "messageId", messageId.toString()));
                                emitter.send(SseEmitter.event().data(doneJson));
                                emitter.complete();
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        }
                );

        return emitter;
    }

    private String buildSystemPrompt(ConversationDto.ChatContext ctx) {
        StringBuilder sb = new StringBuilder(ctx.systemPrompt());
        sb.append("\n\n[말투]: ").append(ctx.speechStyle());
        sb.append("\n[성격]: ").append(ctx.personality());

        if (!ctx.longTermMemories().isEmpty()) {
            sb.append("\n\n[사용자에 대한 기억]");
            for (String memory : ctx.longTermMemories()) {
                sb.append("\n- ").append(memory);
            }
        }

        return sb.toString();
    }
}
