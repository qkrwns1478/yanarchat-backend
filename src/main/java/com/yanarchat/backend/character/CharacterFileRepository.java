package com.yanarchat.backend.character;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CharacterFileRepository extends JpaRepository<CharacterFile, UUID> {
}
