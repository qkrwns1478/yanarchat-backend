package com.yanarchat.backend.character;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CharacterDto.CharacterResponse> createCharacter(
            @RequestParam String name,
            @RequestParam String personaDescription,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(characterService.createCharacter(userId, name, personaDescription, files));
    }

    @GetMapping
    public ResponseEntity<List<CharacterDto.CharacterSummary>> getMyCharacters(
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.ok(characterService.getMyCharacters(userId));
    }

    @GetMapping("/{characterId}")
    public ResponseEntity<CharacterDto.CharacterResponse> getCharacter(
            @PathVariable UUID characterId,
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.ok(characterService.getCharacter(userId, characterId));
    }

    @PutMapping(value = "/{characterId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CharacterDto.CharacterResponse> updateCharacter(
            @PathVariable UUID characterId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String personaDescription,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @AuthenticationPrincipal String userId
    ) {
        return ResponseEntity.ok(characterService.updateCharacter(userId, characterId, name, personaDescription, files));
    }

    @DeleteMapping("/{characterId}")
    public ResponseEntity<Void> deleteCharacter(
            @PathVariable UUID characterId,
            @AuthenticationPrincipal String userId
    ) {
        characterService.deleteCharacter(userId, characterId);
        return ResponseEntity.noContent().build();
    }
}
