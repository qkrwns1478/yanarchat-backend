package com.yanarchat.backend.conversation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Page<Conversation> findByUserId(UUID userId, Pageable pageable);
    Page<Conversation> findByUserIdAndCharacterId(UUID userId, UUID characterId, Pageable pageable);
    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);
}
