package com.yanarchat.backend.conversation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ConversationDto.ConversationResponse> createConversation(
            @RequestBody ConversationDto.CreateRequest request,
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.createConversation(userId, request.characterId(), request.title()));
    }

    @GetMapping
    public ResponseEntity<ConversationDto.ConversationPageResponse> getConversations(
            @RequestParam(required = false) UUID characterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.ok(conversationService.getConversations(userId, characterId, page, size));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ConversationDto.ConversationDetail> getConversation(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.ok(conversationService.getConversation(userId, conversationId, page, size));
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal String userId
    ) {
        conversationService.deleteConversation(userId, conversationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/messages")
    public SseEmitter sendMessage(
            @PathVariable UUID conversationId,
            @RequestBody ConversationDto.SendMessageRequest request,
            @AuthenticationPrincipal String userId
    ) {
        return chatService.chat(userId, conversationId, request.content());
    }
}
