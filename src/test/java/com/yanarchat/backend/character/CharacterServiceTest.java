package com.yanarchat.backend.character;

import com.yanarchat.backend.common.LmStudioService;
import com.yanarchat.backend.user.User;
import com.yanarchat.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CharacterServiceTest {

    @InjectMocks private CharacterService characterService;

    @Mock private CharacterRepository characterRepository;
    @Mock private CharacterFileRepository characterFileRepository;
    @Mock private UserRepository userRepository;
    @Mock private LmStudioService lmStudioService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ObjectMapper objectMapper;

    private final UUID userId = UUID.randomUUID();
    private final UUID characterId = UUID.randomUUID();

    private User mockUser() {
        User user = User.builder().email("test@test.com").username("tester").password("pw").build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Character mockCharacter(User user) {
        Character character = Character.builder()
                .user(user)
                .name("아리아")
                .personaDescription("중세 마법사")
                .speechStyle("격식체")
                .personality("냉정함")
                .traits("[\"마법사\"]")
                .background("왕국 출신")
                .systemPrompt("You are Aria")
                .build();
        ReflectionTestUtils.setField(character, "id", characterId);
        return character;
    }

    private CharacterDto.PersonaAttributes mockPersona() {
        return new CharacterDto.PersonaAttributes(
                "격식체를 사용한다",
                "냉정하지만 따뜻하다",
                List.of("마법사", "검사"),
                "왕국의 수석 마법사",
                "You are Aria, a wizard."
        );
    }

    @Test
    @DisplayName("캐릭터 생성 - 파일 없이 성공")
    void createCharacter_success_withoutFiles() throws Exception {
        // given
        User user = mockUser();
        Character character = mockCharacter(user);

        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(lmStudioService.generatePersona(anyString())).willReturn(mockPersona());
        given(characterRepository.save(any(Character.class))).willReturn(character);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        CharacterDto.CharacterResponse response = characterService.createCharacter(
                userId.toString(), "아리아", "중세 마법사", null);

        // then
        assertNotNull(response);
        verify(lmStudioService, times(1)).generatePersona(anyString());
        verify(characterRepository, times(1)).save(any(Character.class));
    }

    @Test
    @DisplayName("캐릭터 생성 - 이름 누락 시 400 에러")
    void createCharacter_fail_missingName() {
        assertThrows(ResponseStatusException.class,
                () -> characterService.createCharacter(userId.toString(), "", "중세 마법사", null));
    }

    @Test
    @DisplayName("캐릭터 생성 - 설정 누락 시 400 에러")
    void createCharacter_fail_missingPersonaDescription() {
        assertThrows(ResponseStatusException.class,
                () -> characterService.createCharacter(userId.toString(), "아리아", null, null));
    }

    @Test
    @DisplayName("캐릭터 생성 - LM Studio 실패 시 503 에러")
    void createCharacter_fail_lmStudioUnavailable() {
        // given
        User user = mockUser();
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(lmStudioService.generatePersona(anyString()))
                .willThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "LM Studio 연결에 실패했습니다."));

        // when & then
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> characterService.createCharacter(userId.toString(), "아리아", "중세 마법사", null));
        assertEquals(503, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("캐릭터 단건 조회 - Redis 캐시 히트")
    void getCharacter_cacheHit() throws Exception {
        // given
        String cachedJson = "{}";

        CharacterDto.CharacterResponse cachedResponse = new CharacterDto.CharacterResponse(
                characterId, "아리아", "중세 마법사", "격식체", "냉정함",
                List.of(), "왕국 출신", "You are Aria", null, List.of(), null, null);
        CharacterDto.CharacterCacheEntry cacheEntry = new CharacterDto.CharacterCacheEntry(userId, cachedResponse);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("character:" + characterId)).willReturn(cachedJson);
        given(objectMapper.readValue(eq(cachedJson), eq(CharacterDto.CharacterCacheEntry.class))).willReturn(cacheEntry);

        // when
        CharacterDto.CharacterResponse response = characterService.getCharacter(userId.toString(), characterId);

        // then
        assertNotNull(response);
        assertEquals("아리아", response.name());
        verify(characterRepository, never()).findById(any());
    }

    @Test
    @DisplayName("캐릭터 단건 조회 - Redis 미스 후 DB 조회")
    void getCharacter_cacheMiss_dbHit() throws Exception {
        // given
        User user = mockUser();
        Character character = mockCharacter(user);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("character:" + characterId)).willReturn(null);
        given(characterRepository.findById(characterId)).willReturn(Optional.of(character));
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        CharacterDto.CharacterResponse response = characterService.getCharacter(userId.toString(), characterId);

        // then
        assertNotNull(response);
        verify(characterRepository, times(1)).findById(characterId);
        verify(valueOperations, times(1)).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("캐릭터 단건 조회 - 존재하지 않으면 404")
    void getCharacter_notFound() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);
        given(characterRepository.findById(characterId)).willReturn(Optional.empty());

        // when & then
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> characterService.getCharacter(userId.toString(), characterId));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("캐릭터 단건 조회 - 소유자 아니면 403")
    void getCharacter_notOwner() throws Exception {
        // given
        User anotherUser = User.builder().email("other@test.com").username("other").password("pw").build();
        ReflectionTestUtils.setField(anotherUser, "id", UUID.randomUUID());
        Character character = mockCharacter(anotherUser);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("character:" + characterId)).willReturn(null);
        given(characterRepository.findById(characterId)).willReturn(Optional.of(character));

        // when & then
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> characterService.getCharacter(userId.toString(), characterId));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("내 캐릭터 목록 조회 - 성공")
    void getMyCharacters_success() {
        // given
        User user = mockUser();
        Character character = mockCharacter(user);
        given(characterRepository.findByUserId(userId)).willReturn(List.of(character));

        // when
        List<CharacterDto.CharacterSummary> result = characterService.getMyCharacters(userId.toString());

        // then
        assertEquals(1, result.size());
        assertEquals("아리아", result.get(0).name());
    }

    @Test
    @DisplayName("캐릭터 삭제 - 성공")
    void deleteCharacter_success() {
        // given
        User user = mockUser();
        Character character = mockCharacter(user);
        given(characterRepository.findById(characterId)).willReturn(Optional.of(character));
        given(redisTemplate.delete(anyString())).willReturn(true);

        // when
        characterService.deleteCharacter(userId.toString(), characterId);

        // then
        assertNotNull(character.getDeletedAt());
        verify(redisTemplate, times(1)).delete("character:" + characterId);
    }

    @Test
    @DisplayName("캐릭터 삭제 - 소유자 아니면 403")
    void deleteCharacter_notOwner() {
        // given
        User anotherUser = User.builder().email("other@test.com").username("other").password("pw").build();
        ReflectionTestUtils.setField(anotherUser, "id", UUID.randomUUID());
        Character character = mockCharacter(anotherUser);
        given(characterRepository.findById(characterId)).willReturn(Optional.of(character));

        // when & then
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> characterService.deleteCharacter(userId.toString(), characterId));
        assertEquals(403, ex.getStatusCode().value());
    }
}
