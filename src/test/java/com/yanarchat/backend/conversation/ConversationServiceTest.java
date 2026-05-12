package com.yanarchat.backend.conversation;

import com.yanarchat.backend.character.Character;
import com.yanarchat.backend.character.CharacterRepository;
import com.yanarchat.backend.memory.Memory;
import com.yanarchat.backend.memory.MemoryRepository;
import com.yanarchat.backend.user.User;
import com.yanarchat.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @InjectMocks private ConversationService conversationService;

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private CharacterRepository characterRepository;
    @Mock private UserRepository userRepository;
    @Mock private MemoryRepository memoryRepository;

    private final UUID userId = UUID.randomUUID();
    private final UUID characterId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    private User mockUser() {
        User user = User.builder().email("test@test.com").username("tester").password("pw").build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Character mockCharacter(User user) {
        Character character = Character.builder()
                .user(user).name("아리아").personaDescription("중세 마법사")
                .speechStyle("격식체").personality("냉정함")
                .traits("[\"마법사\"]").background("왕국 출신")
                .systemPrompt("You are Aria")
                .build();
        ReflectionTestUtils.setField(character, "id", characterId);
        return character;
    }

    private Conversation mockConversation(User user, Character character) {
        Conversation conversation = Conversation.builder()
                .user(user).character(character).title("첫 번째 대화")
                .build();
        ReflectionTestUtils.setField(conversation, "id", conversationId);
        return conversation;
    }

    // ── createConversation ──────────────────────────────────────────────────

    @Test
    @DisplayName("대화 세션 생성 - 성공")
    void createConversation_success() {
        User user = mockUser();
        Character character = mockCharacter(user);
        Conversation conversation = mockConversation(user, character);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(characterRepository.findById(characterId)).willReturn(Optional.of(character));
        given(conversationRepository.save(any())).willReturn(conversation);

        ConversationDto.ConversationResponse response =
                conversationService.createConversation(userId.toString(), characterId, "첫 번째 대화");

        assertNotNull(response);
        assertEquals("아리아", response.characterName());
        assertEquals("첫 번째 대화", response.title());
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    @DisplayName("대화 세션 생성 - 존재하지 않는 캐릭터 404")
    void createConversation_characterNotFound() {
        given(userRepository.findById(userId)).willReturn(Optional.of(mockUser()));
        given(characterRepository.findById(characterId)).willReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> conversationService.createConversation(userId.toString(), characterId, "제목"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("대화 세션 생성 - 남의 캐릭터로 생성 시 403")
    void createConversation_notOwnerCharacter() {
        User user = mockUser();
        User another = User.builder().email("x@x.com").username("x").password("pw").build();
        ReflectionTestUtils.setField(another, "id", UUID.randomUUID());
        Character character = mockCharacter(another);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(characterRepository.findById(characterId)).willReturn(Optional.of(character));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> conversationService.createConversation(userId.toString(), characterId, "제목"));
        assertEquals(403, ex.getStatusCode().value());
    }

    // ── getConversations ────────────────────────────────────────────────────

    @Test
    @DisplayName("대화 목록 조회 - characterId 없이 전체 조회")
    void getConversations_withoutCharacterFilter() {
        User user = mockUser();
        Character character = mockCharacter(user);
        Conversation conversation = mockConversation(user, character);
        Page<Conversation> page = new PageImpl<>(List.of(conversation));

        given(conversationRepository.findByUserId(eq(userId), any(Pageable.class))).willReturn(page);

        ConversationDto.ConversationPageResponse response =
                conversationService.getConversations(userId.toString(), null, 0, 20);

        assertEquals(1, response.totalElements());
        assertEquals(1, response.content().size());
        assertEquals("아리아", response.content().get(0).characterName());
    }

    @Test
    @DisplayName("대화 목록 조회 - characterId로 필터링")
    void getConversations_withCharacterFilter() {
        User user = mockUser();
        Character character = mockCharacter(user);
        Conversation conversation = mockConversation(user, character);
        Page<Conversation> page = new PageImpl<>(List.of(conversation));

        given(conversationRepository.findByUserIdAndCharacterId(eq(userId), eq(characterId), any(Pageable.class)))
                .willReturn(page);

        ConversationDto.ConversationPageResponse response =
                conversationService.getConversations(userId.toString(), characterId, 0, 20);

        assertEquals(1, response.totalElements());
        verify(conversationRepository, never()).findByUserId(any(), any());
    }

    // ── getConversation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("대화 상세 조회 - 성공 (메시지 포함)")
    void getConversation_success() {
        User user = mockUser();
        Character character = mockCharacter(user);
        Conversation conversation = mockConversation(user, character);

        Message msg = Message.builder()
                .conversation(conversation).role(MessageRole.USER).content("안녕하세요").build();
        ReflectionTestUtils.setField(msg, "id", UUID.randomUUID());

        given(conversationRepository.findByIdAndUserId(conversationId, userId))
                .willReturn(Optional.of(conversation));
        given(messageRepository.findByConversationIdOrderByCreatedAtAsc(eq(conversationId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(msg)));

        ConversationDto.ConversationDetail detail =
                conversationService.getConversation(userId.toString(), conversationId, 0, 50);

        assertEquals("아리아", detail.characterName());
        assertEquals(1, detail.messages().size());
        assertEquals("USER", detail.messages().get(0).role());
        assertEquals("안녕하세요", detail.messages().get(0).content());
    }

    @Test
    @DisplayName("대화 상세 조회 - 존재하지 않거나 타인 소유 시 404")
    void getConversation_notFound() {
        given(conversationRepository.findByIdAndUserId(conversationId, userId))
                .willReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> conversationService.getConversation(userId.toString(), conversationId, 0, 50));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ── deleteConversation ──────────────────────────────────────────────────

    @Test
    @DisplayName("대화 세션 삭제 - Soft Delete 성공")
    void deleteConversation_success() {
        User user = mockUser();
        Character character = mockCharacter(user);
        Conversation conversation = mockConversation(user, character);

        given(conversationRepository.findByIdAndUserId(conversationId, userId))
                .willReturn(Optional.of(conversation));

        conversationService.deleteConversation(userId.toString(), conversationId);

        assertNotNull(conversation.getDeletedAt());
    }

    @Test
    @DisplayName("대화 세션 삭제 - 없는 세션이면 404")
    void deleteConversation_notFound() {
        given(conversationRepository.findByIdAndUserId(conversationId, userId))
                .willReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> conversationService.deleteConversation(userId.toString(), conversationId));
        assertEquals(404, ex.getStatusCode().value());
    }

    // ── prepareChat ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("채팅 준비 - 유저 메시지 저장 및 ChatContext 반환")
    void prepareChat_success() {
        User user = mockUser();
        Character character = mockCharacter(user);
        Conversation conversation = mockConversation(user, character);

        given(conversationRepository.findByIdAndUserId(conversationId, userId))
                .willReturn(Optional.of(conversation));
        given(memoryRepository.findByCharacterIdAndUserIdAndMemoryType(characterId, userId, "LONG_TERM"))
                .willReturn(List.of());
        given(messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversationId))
                .willReturn(List.of());
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> {
            Message m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
            return m;
        });

        ConversationDto.ChatContext ctx =
                conversationService.prepareChat(userId.toString(), conversationId, "안녕!");

        assertNotNull(ctx);
        assertEquals("안녕!", ctx.lastUserMessage());
        assertEquals(characterId, ctx.characterId());
        assertEquals(userId, ctx.userId());
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    @DisplayName("채팅 준비 - 장기 메모리가 있으면 ChatContext에 포함")
    void prepareChat_withLongTermMemory() {
        User user = mockUser();
        Character character = mockCharacter(user);
        Conversation conversation = mockConversation(user, character);

        Memory memory = Memory.builder()
                .character(character).user(user)
                .content("사용자의 이름은 철수다.").memoryType("LONG_TERM")
                .build();

        given(conversationRepository.findByIdAndUserId(conversationId, userId))
                .willReturn(Optional.of(conversation));
        given(memoryRepository.findByCharacterIdAndUserIdAndMemoryType(characterId, userId, "LONG_TERM"))
                .willReturn(List.of(memory));
        given(messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversationId))
                .willReturn(List.of());
        given(messageRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ConversationDto.ChatContext ctx =
                conversationService.prepareChat(userId.toString(), conversationId, "안녕");

        assertEquals(1, ctx.longTermMemories().size());
        assertEquals("사용자의 이름은 철수다.", ctx.longTermMemories().get(0));
    }

    // ── saveAssistantMessage ────────────────────────────────────────────────

    @Test
    @DisplayName("어시스턴트 메시지 저장 - 성공 후 UUID 반환")
    void saveAssistantMessage_success() {
        User user = mockUser();
        Character character = mockCharacter(user);
        Conversation conversation = mockConversation(user, character);

        given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
        given(messageRepository.save(any(Message.class))).willAnswer(inv -> {
            Message m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
            return m;
        });

        UUID messageId = conversationService.saveAssistantMessage(conversationId, "안녕하세요!");

        assertNotNull(messageId);
        verify(messageRepository).save(argThat(m -> m.getRole() == MessageRole.ASSISTANT));
    }

    @Test
    @DisplayName("어시스턴트 메시지 저장 - 세션 없으면 404")
    void saveAssistantMessage_conversationNotFound() {
        given(conversationRepository.findById(conversationId)).willReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> conversationService.saveAssistantMessage(conversationId, "응답"));
        assertEquals(404, ex.getStatusCode().value());
    }
}
