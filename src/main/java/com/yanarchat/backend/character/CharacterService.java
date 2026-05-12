package com.yanarchat.backend.character;

import com.yanarchat.backend.common.LmStudioService;
import com.yanarchat.backend.user.User;
import com.yanarchat.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final CharacterFileRepository characterFileRepository;
    private final UserRepository userRepository;
    private final LmStudioService lmStudioService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY_PREFIX = "character:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final List<String> ALLOWED_EXTENSIONS = List.of("txt", "json");

    @Value("${file.upload.dir:./uploads}")
    private String uploadDir;

    @Transactional
    public CharacterDto.CharacterResponse createCharacter(String userId, String name, String personaDescription,
                                                           List<MultipartFile> files) {
        validateInput(name, personaDescription);

        User user = findUser(userId);

        String combinedText = buildCombinedText(personaDescription, files);
        CharacterDto.PersonaAttributes persona = lmStudioService.generatePersona(combinedText);

        Character character = Character.builder()
                .user(user)
                .name(name)
                .personaDescription(personaDescription)
                .speechStyle(persona.speechStyle())
                .personality(persona.personality())
                .traits(traitsToJson(persona.traits()))
                .background(persona.background())
                .systemPrompt(persona.systemPrompt())
                .build();

        characterRepository.save(character);

        if (files != null && !files.isEmpty()) {
            saveFiles(character, files);
        }

        CharacterDto.CharacterResponse response = toResponse(character);
        cacheCharacter(user.getId(), character.getId(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public CharacterDto.CharacterResponse getCharacter(String userId, UUID characterId) {
        String cached = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + characterId);
        if (cached != null) {
            CharacterDto.CharacterCacheEntry entry = deserializeEntry(cached);
            if (entry != null) {
                validateOwner(userId, entry.ownerId());
                return entry.response();
            }
        }

        Character character = findCharacterById(characterId);
        validateOwner(userId, character.getUser().getId());

        CharacterDto.CharacterResponse response = toResponse(character);
        cacheCharacter(character.getUser().getId(), characterId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<CharacterDto.CharacterSummary> getMyCharacters(String userId) {
        UUID userUUID = UUID.fromString(userId);
        return characterRepository.findByUserId(userUUID).stream()
                .map(c -> new CharacterDto.CharacterSummary(
                        c.getId(), c.getName(), c.getPersonality(), c.getAvatarUrl(), c.getCreatedAt()))
                .toList();
    }

    @Transactional
    public CharacterDto.CharacterResponse updateCharacter(String userId, UUID characterId,
                                                           String name, String personaDescription,
                                                           List<MultipartFile> files) {
        Character character = findCharacterById(characterId);
        validateOwner(userId, character.getUser().getId());

        if (personaDescription != null || (files != null && !files.isEmpty())) {
            String desc = personaDescription != null ? personaDescription : character.getPersonaDescription();
            String combinedText = buildCombinedText(desc, files);
            CharacterDto.PersonaAttributes persona = lmStudioService.generatePersona(combinedText);
            character.updatePersona(desc, persona.speechStyle(), persona.personality(),
                    traitsToJson(persona.traits()), persona.background(), persona.systemPrompt());

            if (files != null && !files.isEmpty()) {
                saveFiles(character, files);
            }
        }

        if (name != null) {
            character.updateName(name);
        }

        invalidateCache(characterId);
        CharacterDto.CharacterResponse response = toResponse(character);
        cacheCharacter(character.getUser().getId(), characterId, response);
        return response;
    }

    @Transactional
    public void deleteCharacter(String userId, UUID characterId) {
        Character character = findCharacterById(characterId);
        validateOwner(userId, character.getUser().getId());
        character.markAsDeleted();
        invalidateCache(characterId);
    }

    private void validateInput(String name, String personaDescription) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "캐릭터 이름은 필수입니다.");
        }
        if (personaDescription == null || personaDescription.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "캐릭터 설정 텍스트는 필수입니다.");
        }
    }

    private User findUser(String userId) {
        return userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private Character findCharacterById(UUID characterId) {
        return characterRepository.findById(characterId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "캐릭터를 찾을 수 없습니다."));
    }

    private void validateOwner(String userId, UUID ownerId) {
        if (!ownerId.toString().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 캐릭터에 접근할 권한이 없습니다.");
        }
    }

    private String buildCombinedText(String personaDescription, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return personaDescription;
        }

        StringBuilder sb = new StringBuilder(personaDescription);
        for (MultipartFile file : files) {
            String ext = getExtension(file.getOriginalFilename());
            if (!ALLOWED_EXTENSIONS.contains(ext)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "지원하지 않는 파일 형식입니다. TXT 또는 JSON만 허용됩니다.");
            }
            try {
                String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                sb.append("\n\n[첨부파일: ").append(file.getOriginalFilename()).append("]\n").append(content);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 읽기에 실패했습니다.");
            }
        }
        return sb.toString();
    }

    private void saveFiles(Character character, List<MultipartFile> files) {
        Path dir = Paths.get(uploadDir, character.getId().toString());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장 디렉토리 생성에 실패했습니다.");
        }

        for (MultipartFile file : files) {
            String ext = getExtension(file.getOriginalFilename());
            String storedName = UUID.randomUUID() + "." + ext;
            Path target = dir.resolve(storedName);
            try {
                Files.copy(file.getInputStream(), target);
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다.");
            }

            CharacterFile characterFile = CharacterFile.builder()
                    .character(character)
                    .originalName(file.getOriginalFilename())
                    .fileType(ext.toUpperCase())
                    .storagePath(target.toString())
                    .build();
            characterFileRepository.save(characterFile);
            character.getFiles().add(characterFile);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 확장자를 확인할 수 없습니다.");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private CharacterDto.CharacterResponse toResponse(Character character) {
        List<CharacterDto.FileInfo> fileInfos = character.getFiles().stream()
                .map(f -> new CharacterDto.FileInfo(f.getId(), f.getOriginalName(), f.getFileType()))
                .toList();

        return new CharacterDto.CharacterResponse(
                character.getId(),
                character.getName(),
                character.getPersonaDescription(),
                character.getSpeechStyle(),
                character.getPersonality(),
                jsonToTraits(character.getTraits()),
                character.getBackground(),
                character.getSystemPrompt(),
                character.getAvatarUrl(),
                fileInfos,
                character.getCreatedAt(),
                character.getUpdatedAt()
        );
    }

    private void cacheCharacter(UUID ownerId, UUID characterId, CharacterDto.CharacterResponse response) {
        try {
            CharacterDto.CharacterCacheEntry entry = new CharacterDto.CharacterCacheEntry(ownerId, response);
            String json = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + characterId, json, CACHE_TTL);
        } catch (Exception ignored) {
        }
    }

    private void invalidateCache(UUID characterId) {
        redisTemplate.delete(CACHE_KEY_PREFIX + characterId);
    }

    private CharacterDto.CharacterCacheEntry deserializeEntry(String json) {
        try {
            return objectMapper.readValue(json, CharacterDto.CharacterCacheEntry.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String traitsToJson(List<String> traits) {
        try {
            return objectMapper.writeValueAsString(traits);
        } catch (Exception e) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> jsonToTraits(String json) {
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
