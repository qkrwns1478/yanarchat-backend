package com.yanarchat.backend.conversation;

import com.yanarchat.backend.auth.AuthDto;
import com.yanarchat.backend.auth.AuthService;
import com.yanarchat.backend.character.CharacterDto;
import com.yanarchat.backend.character.CharacterService;
import com.yanarchat.backend.common.LmStudioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversationControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private CharacterService characterService;

    @MockitoBean private LmStudioService lmStudioService;

    private String accessToken;
    private UUID characterId;

    @BeforeEach
    void setUp() {
        given(lmStudioService.generatePersona(anyString())).willReturn(new CharacterDto.PersonaAttributes(
                "격식체", "냉정함", List.of("마법사"), "왕국 출신", "You are Aria"));

        String email = "conv-test-" + UUID.randomUUID() + "@example.com";
        AuthDto.TokenResponse tokens = authService.signup(new AuthDto.SignupRequest(email, "tester", "password123!"));
        accessToken = tokens.accessToken();

        CharacterDto.CharacterResponse character = characterService.createCharacter(
                parseUserId(accessToken), "아리아", "중세 마법사", null);
        characterId = character.id();
    }

    private String parseUserId(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        try {
            return (String) objectMapper.readValue(payload, Map.class).get("sub");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String createConversation(String title) throws Exception {
        return mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("characterId", characterId.toString(), "title", title))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    // ── POST /api/conversations ─────────────────────────────────────────────

    @Test
    @DisplayName("대화 세션 생성 - 성공 (201)")
    void createConversation_success() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("characterId", characterId.toString(), "title", "첫 번째 대화"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.characterName").value("아리아"))
                .andExpect(jsonPath("$.title").value("첫 번째 대화"));
    }

    @Test
    @DisplayName("대화 세션 생성 - 인증 없이 요청 시 403")
    void createConversation_unauthorized() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("characterId", characterId.toString(), "title", "제목"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("대화 세션 생성 - 존재하지 않는 캐릭터 404")
    void createConversation_characterNotFound() throws Exception {
        mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("characterId", UUID.randomUUID().toString(), "title", "제목"))))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/conversations ──────────────────────────────────────────────

    @Test
    @DisplayName("대화 목록 조회 - 성공 (200)")
    void getConversations_success() throws Exception {
        createConversation("대화 1");
        createConversation("대화 2");

        mockMvc.perform(get("/api/conversations")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("대화 목록 조회 - characterId 필터링")
    void getConversations_withCharacterFilter() throws Exception {
        createConversation("대화 1");

        mockMvc.perform(get("/api/conversations")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("characterId", characterId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].characterId").value(characterId.toString()));
    }

    @Test
    @DisplayName("대화 목록 조회 - 빈 목록 반환")
    void getConversations_empty() throws Exception {
        mockMvc.perform(get("/api/conversations")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // ── GET /api/conversations/{id} ─────────────────────────────────────────

    @Test
    @DisplayName("대화 상세 조회 - 성공 (200)")
    void getConversation_success() throws Exception {
        String body = createConversation("테스트 대화");
        String conversationId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId))
                .andExpect(jsonPath("$.characterName").value("아리아"))
                .andExpect(jsonPath("$.messages").isArray());
    }

    @Test
    @DisplayName("대화 상세 조회 - 없는 세션 404")
    void getConversation_notFound() throws Exception {
        mockMvc.perform(get("/api/conversations/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("대화 상세 조회 - 타인 세션 접근 시 404")
    void getConversation_otherUserConversation() throws Exception {
        // 다른 유저 생성 후 대화 세션 생성
        String otherEmail = "other-" + UUID.randomUUID() + "@example.com";
        AuthDto.TokenResponse otherTokens = authService.signup(
                new AuthDto.SignupRequest(otherEmail, "other", "password123!"));
        String otherToken = otherTokens.accessToken();

        given(lmStudioService.generatePersona(anyString())).willReturn(new CharacterDto.PersonaAttributes(
                "격식체", "냉정함", List.of("마법사"), "왕국 출신", "You are Aria"));

        CharacterDto.CharacterResponse otherCharacter = characterService.createCharacter(
                parseUserId(otherToken), "다른캐릭터", "설명", null);

        String body = mockMvc.perform(post("/api/conversations")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("characterId", otherCharacter.id().toString(), "title", "타인 대화"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String otherConversationId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        // 원래 유저로 타인 대화 접근
        mockMvc.perform(get("/api/conversations/" + otherConversationId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/conversations/{id} ──────────────────────────────────────

    @Test
    @DisplayName("대화 세션 삭제 - 성공 (204)")
    void deleteConversation_success() throws Exception {
        String body = createConversation("삭제할 대화");
        String conversationId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(delete("/api/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("대화 세션 삭제 후 조회 - 404 반환")
    void deleteConversation_thenGet_notFound() throws Exception {
        String body = createConversation("삭제할 대화");
        String conversationId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(delete("/api/conversations/" + conversationId)
                .header("Authorization", "Bearer " + accessToken));

        mockMvc.perform(get("/api/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("대화 세션 삭제 후 목록에서 제외됨")
    void deleteConversation_excludedFromList() throws Exception {
        createConversation("남길 대화");
        String toDelete = createConversation("삭제할 대화");
        String conversationId = com.jayway.jsonpath.JsonPath.read(toDelete, "$.id");

        mockMvc.perform(delete("/api/conversations/" + conversationId)
                .header("Authorization", "Bearer " + accessToken));

        mockMvc.perform(get("/api/conversations")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
