package com.yanarchat.backend.memory;

import com.yanarchat.backend.character.Character;
import com.yanarchat.backend.character.CharacterRepository;
import com.yanarchat.backend.common.LmStudioService;
import com.yanarchat.backend.user.User;
import com.yanarchat.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @InjectMocks private MemoryService memoryService;

    @Mock private MemoryRepository memoryRepository;
    @Mock private CharacterRepository characterRepository;
    @Mock private UserRepository userRepository;
    @Mock private LmStudioService lmStudioService;

    private final UUID userId = UUID.randomUUID();
    private final UUID characterId = UUID.randomUUID();
    private final UUID memoryId = UUID.randomUUID();

    private User mockUser() {
        User user = User.builder().email("test@test.com").username("tester").password("pw").build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Character mockCharacter(User user) {
        Character character = Character.builder()
                .user(user).name("아리아").personaDescription("설명")
                .speechStyle("격식체").personality("냉정함")
                .traits("[]").background("배경").systemPrompt("prompt")
                .build();
        ReflectionTestUtils.setField(character, "id", characterId);
        return character;
    }

    private Memory mockMemory(Character character, User user) {
        Memory memory = Memory.builder()
                .character(character).user(user)
                .content("사용자의 이름은 철수다.").memoryType("LONG_TERM")
                .build();
        ReflectionTestUtils.setField(memory, "id", memoryId);
        return memory;
    }

    // ── getMemories ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("메모리 목록 조회 - 타입 없이 전체 조회 성공")
    void getMemories_withoutType() {
        User user = mockUser();
        Character character = mockCharacter(user);
        Memory memory = mockMemory(character, user);

        given(characterRepository.findById(characterId)).willReturn(Optional.of(character));
        given(memoryRepository.findByCharacterIdAndUserId(characterId, userId)).willReturn(List.of(memory));

        List<MemoryDto.MemoryResponse> result = memoryService.getMemories(userId.toString(), characterId, null);

        assertEquals(1, result.size());
        assertEquals("사용자의 이름은 철수다.", result.get(0).content());
        assertEquals("LONG_TERM", result.get(0).memoryType());
    }

    @Test
    @DisplayName("메모리 목록 조회 - LONG_TERM 타입 필터")
    void getMemories_withLongTermFilter() {
        User user = mockUser();
        Character character = mockCharacter(user);
        Memory memory = mockMemory(character, user);

        given(characterRepository.findById(characterId)).willReturn(Optional.of(character));
        given(memoryRepository.findByCharacterIdAndUserIdAndMemoryType(characterId, userId, "LONG_TERM"))
                .willReturn(List.of(memory));

        List<MemoryDto.MemoryResponse> result =
                memoryService.getMemories(userId.toString(), characterId, "LONG_TERM");

        assertEquals(1, result.size());
        verify(memoryRepository, never()).findByCharacterIdAndUserId(any(), any());
    }

    @Test
    @DisplayName("메모리 목록 조회 - 존재하지 않는 캐릭터 404")
    void getMemories_characterNotFound() {
        given(characterRepository.findById(characterId)).willReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> memoryService.getMemories(userId.toString(), characterId, null));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("메모리 목록 조회 - 남의 캐릭터 403")
    void getMemories_notOwner() {
        User user = mockUser();
        User another = User.builder().email("x@x.com").username("x").password("pw").build();
        ReflectionTestUtils.setField(another, "id", UUID.randomUUID());
        Character character = mockCharacter(another);

        given(characterRepository.findById(characterId)).willReturn(Optional.of(character));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> memoryService.getMemories(userId.toString(), characterId, null));
        assertEquals(403, ex.getStatusCode().value());
    }

    // ── deleteMemory ────────────────────────────────────────────────────────

    @Test
    @DisplayName("메모리 삭제 - Soft Delete 성공")
    void deleteMemory_success() {
        User user = mockUser();
        Character character = mockCharacter(user);
        Memory memory = mockMemory(character, user);

        given(characterRepository.findById(characterId)).willReturn(Optional.of(character));
        given(memoryRepository.findByIdAndCharacterIdAndUserId(memoryId, characterId, userId))
                .willReturn(Optional.of(memory));

        memoryService.deleteMemory(userId.toString(), characterId, memoryId);

        assertNotNull(memory.getDeletedAt());
    }

    @Test
    @DisplayName("메모리 삭제 - 존재하지 않으면 404")
    void deleteMemory_notFound() {
        User user = mockUser();
        Character character = mockCharacter(user);

        given(characterRepository.findById(characterId)).willReturn(Optional.of(character));
        given(memoryRepository.findByIdAndCharacterIdAndUserId(memoryId, characterId, userId))
                .willReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> memoryService.deleteMemory(userId.toString(), characterId, memoryId));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ── extractAndSave ──────────────────────────────────────────────────────

    @Test
    @DisplayName("메모리 추출 - 중요 정보 감지 시 DB 저장")
    void extractAndSave_importantInfo() {
        given(lmStudioService.extractMemory(anyString(), anyString()))
                .willReturn("사용자의 이름은 철수다.");
        given(characterRepository.getReferenceById(characterId))
                .willReturn(mock(Character.class));
        given(userRepository.getReferenceById(userId))
                .willReturn(mock(User.class));

        memoryService.extractAndSave(characterId, userId, "내 이름은 철수야", "그렇군요!");

        verify(memoryRepository).save(argThat(m -> m.getMemoryType().equals("LONG_TERM")));
    }

    @Test
    @DisplayName("메모리 추출 - NONE 응답 시 저장 안 함")
    void extractAndSave_none() {
        given(lmStudioService.extractMemory(anyString(), anyString())).willReturn("NONE");

        memoryService.extractAndSave(characterId, userId, "오늘 날씨 어때?", "맑아요.");

        verify(memoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("메모리 추출 - LM Studio 실패 시 저장 안 함")
    void extractAndSave_lmStudioFailure() {
        given(lmStudioService.extractMemory(anyString(), anyString())).willReturn(null);

        memoryService.extractAndSave(characterId, userId, "안녕", "안녕하세요");

        verify(memoryRepository, never()).save(any());
    }
}
