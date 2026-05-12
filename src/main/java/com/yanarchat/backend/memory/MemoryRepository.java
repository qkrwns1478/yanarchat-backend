package com.yanarchat.backend.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemoryRepository extends JpaRepository<Memory, UUID> {
    List<Memory> findByCharacterIdAndUserId(UUID characterId, UUID userId);
    List<Memory> findByCharacterIdAndUserIdAndMemoryType(UUID characterId, UUID userId, String memoryType);
    Optional<Memory> findByIdAndCharacterIdAndUserId(UUID id, UUID characterId, UUID userId);
}
