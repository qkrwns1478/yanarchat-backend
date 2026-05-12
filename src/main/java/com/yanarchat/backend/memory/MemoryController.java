package com.yanarchat.backend.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/characters/{characterId}/memories")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryService memoryService;

    @GetMapping
    public ResponseEntity<List<MemoryDto.MemoryResponse>> getMemories(
            @PathVariable UUID characterId,
            @RequestParam(required = false) String type,
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.ok(memoryService.getMemories(userId, characterId, type));
    }

    @DeleteMapping("/{memoryId}")
    public ResponseEntity<Void> deleteMemory(
            @PathVariable UUID characterId,
            @PathVariable UUID memoryId,
            @AuthenticationPrincipal String userId
    ) {
        memoryService.deleteMemory(userId, characterId, memoryId);
        return ResponseEntity.noContent().build();
    }
}
