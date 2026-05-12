package com.yanarchat.backend.conversation;

import com.yanarchat.backend.character.Character;
import com.yanarchat.backend.character.CharacterRepository;
import com.yanarchat.backend.memory.MemoryRepository;
import com.yanarchat.backend.user.User;
import com.yanarchat.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final CharacterRepository characterRepository;
    private final UserRepository userRepository;
    private final MemoryRepository memoryRepository;

    @Transactional
    public ConversationDto.ConversationResponse createConversation(String userId, UUID characterId, String title) {
        User user = findUser(userId);
        Character character = findCharacter(characterId);
        validateCharacterOwner(userId, character);

        Conversation conversation = Conversation.builder()
                .user(user)
                .character(character)
                .title(title)
                .build();

        conversationRepository.save(conversation);

        return new ConversationDto.ConversationResponse(
                conversation.getId(),
                character.getId(),
                character.getName(),
                conversation.getTitle(),
                conversation.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public ConversationDto.ConversationPageResponse getConversations(String userId, UUID characterId, int page, int size) {
        UUID userUUID = UUID.fromString(userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        Page<Conversation> pageResult = characterId != null
                ? conversationRepository.findByUserIdAndCharacterId(userUUID, characterId, pageable)
                : conversationRepository.findByUserId(userUUID, pageable);

        List<ConversationDto.ConversationSummary> content = pageResult.getContent().stream()
                .map(c -> new ConversationDto.ConversationSummary(
                        c.getId(),
                        c.getCharacter().getId(),
                        c.getCharacter().getName(),
                        c.getTitle(),
                        c.getUpdatedAt()
                ))
                .toList();

        return new ConversationDto.ConversationPageResponse(
                content, pageResult.getTotalElements(), pageResult.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ConversationDto.ConversationDetail getConversation(String userId, UUID conversationId, int page, int size) {
        Conversation conversation = findConversationForUser(userId, conversationId);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        List<ConversationDto.MessageResponse> messages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId, pageable)
                .getContent().stream()
                .map(m -> new ConversationDto.MessageResponse(
                        m.getId(), m.getRole().name(), m.getContent(), m.getCreatedAt()))
                .toList();

        return new ConversationDto.ConversationDetail(
                conversation.getId(),
                conversation.getCharacter().getId(),
                conversation.getCharacter().getName(),
                conversation.getTitle(),
                messages
        );
    }

    @Transactional
    public void deleteConversation(String userId, UUID conversationId) {
        Conversation conversation = findConversationForUser(userId, conversationId);
        conversation.markAsDeleted();
    }

    @Transactional
    public ConversationDto.ChatContext prepareChat(String userId, UUID conversationId, String content) {
        Conversation conversation = findConversationForUser(userId, conversationId);
        Character character = conversation.getCharacter();
        UUID userUUID = UUID.fromString(userId);

        Message userMessage = Message.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .content(content)
                .build();
        messageRepository.save(userMessage);

        List<String> longTermMemories = memoryRepository
                .findByCharacterIdAndUserIdAndMemoryType(character.getId(), userUUID, "LONG_TERM")
                .stream()
                .map(m -> m.getContent())
                .toList();

        List<Message> recent = messageRepository
                .findTop20ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(recent);

        List<Map<String, String>> chatHistory = recent.stream()
                .map(m -> Map.of("role", m.getRole().name().toLowerCase(), "content", m.getContent()))
                .toList();

        return new ConversationDto.ChatContext(
                conversationId,
                character.getId(),
                userUUID,
                character.getSystemPrompt(),
                character.getSpeechStyle(),
                character.getPersonality(),
                chatHistory,
                longTermMemories,
                content
        );
    }

    @Transactional
    public UUID saveAssistantMessage(UUID conversationId, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대화 세션을 찾을 수 없습니다."));

        Message message = Message.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .content(content)
                .build();
        messageRepository.save(message);
        return message.getId();
    }

    private Conversation findConversationForUser(String userId, UUID conversationId) {
        UUID userUUID = UUID.fromString(userId);
        return conversationRepository.findByIdAndUserId(conversationId, userUUID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "대화 세션을 찾을 수 없습니다."));
    }

    private User findUser(String userId) {
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private Character findCharacter(UUID characterId) {
        return characterRepository.findById(characterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "캐릭터를 찾을 수 없습니다."));
    }

    private void validateCharacterOwner(String userId, Character character) {
        if (!character.getUser().getId().toString().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 캐릭터에 접근할 권한이 없습니다.");
        }
    }
}
