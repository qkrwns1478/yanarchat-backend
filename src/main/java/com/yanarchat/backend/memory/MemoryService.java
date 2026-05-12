package com.yanarchat.backend.memory;

import com.yanarchat.backend.character.Character;
import com.yanarchat.backend.character.CharacterRepository;
import com.yanarchat.backend.common.LmStudioService;
import com.yanarchat.backend.user.User;
import com.yanarchat.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final CharacterRepository characterRepository;
    private final UserRepository userRepository;
    private final LmStudioService lmStudioService;

    @Transactional(readOnly = true)
    public List<MemoryDto.MemoryResponse> getMemories(String userId, UUID characterId, String memoryType) {
        UUID userUUID = UUID.fromString(userId);
        validateCharacterAccess(userId, characterId);

        List<Memory> memories = memoryType != null
                ? memoryRepository.findByCharacterIdAndUserIdAndMemoryType(characterId, userUUID, memoryType.toUpperCase())
                : memoryRepository.findByCharacterIdAndUserId(characterId, userUUID);

        return memories.stream()
                .map(m -> new MemoryDto.MemoryResponse(m.getId(), m.getContent(), m.getMemoryType(), m.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void deleteMemory(String userId, UUID characterId, UUID memoryId) {
        UUID userUUID = UUID.fromString(userId);
        validateCharacterAccess(userId, characterId);

        Memory memory = memoryRepository.findByIdAndCharacterIdAndUserId(memoryId, characterId, userUUID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "메모리를 찾을 수 없습니다."));

        memory.markAsDeleted();
    }

    @Transactional
    public void extractAndSave(UUID characterId, UUID userId, String userMessage, String assistantMessage) {
        String extracted = lmStudioService.extractMemory(userMessage, assistantMessage);
        if (extracted == null || extracted.isBlank() || extracted.equalsIgnoreCase("NONE")) {
            return;
        }

        Character character = characterRepository.getReferenceById(characterId);
        User user = userRepository.getReferenceById(userId);

        Memory memory = Memory.builder()
                .character(character)
                .user(user)
                .content(extracted)
                .memoryType("LONG_TERM")
                .build();

        memoryRepository.save(memory);
    }

    private void validateCharacterAccess(String userId, UUID characterId) {
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "캐릭터를 찾을 수 없습니다."));
        if (!character.getUser().getId().toString().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 캐릭터에 접근할 권한이 없습니다.");
        }
    }
}
