package com.yanarchat.backend.character;

import com.yanarchat.backend.auth.AuthDto;
import com.yanarchat.backend.auth.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CharacterControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;

    @MockitoBean private LmStudioService lmStudioService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        AuthDto.SignupRequest signupRequest = new AuthDto.SignupRequest(
                "char-test-" + UUID.randomUUID() + "@example.com", "tester", "password123!");
        AuthDto.TokenResponse tokens = authService.signup(signupRequest);
        accessToken = tokens.accessToken();

        given(lmStudioService.generatePersona(anyString())).willReturn(
                new CharacterDto.PersonaAttributes(
                        "격식체를 사용한다",
                        "냉정하지만 따뜻하다",
                        List.of("마법사", "검사"),
                        "왕국의 수석 마법사",
                        "You are Aria, a wizard."
                )
        );
    }

    @Test
    @DisplayName("캐릭터 생성 - 파일 없이 성공 (201)")
    void createCharacter_success_withoutFiles() throws Exception {
        mockMvc.perform(multipart("/api/characters")
                        .param("name", "아리아")
                        .param("personaDescription", "중세 시대 왕국의 마법사")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("아리아"))
                .andExpect(jsonPath("$.speechStyle").value("격식체를 사용한다"))
                .andExpect(jsonPath("$.personality").value("냉정하지만 따뜻하다"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @DisplayName("캐릭터 생성 - TXT 파일 첨부 성공 (201)")
    void createCharacter_success_withTxtFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "detail.txt", MediaType.TEXT_PLAIN_VALUE,
                "아리아는 어릴 때부터 마법에 재능이 있었다.".getBytes()
        );

        mockMvc.perform(multipart("/api/characters")
                        .file(file)
                        .param("name", "아리아")
                        .param("personaDescription", "중세 시대 왕국의 마법사")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("아리아"))
                .andExpect(jsonPath("$.files").isArray());
    }

    @Test
    @DisplayName("캐릭터 생성 - 인증 없이 요청 시 403 (Spring Security 기본값)")
    void createCharacter_unauthorized() throws Exception {
        mockMvc.perform(multipart("/api/characters")
                        .param("name", "아리아")
                        .param("personaDescription", "중세 마법사"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("캐릭터 생성 - 지원하지 않는 파일 형식 시 400")
    void createCharacter_unsupportedFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "image.png", MediaType.IMAGE_PNG_VALUE, "data".getBytes()
        );

        mockMvc.perform(multipart("/api/characters")
                        .file(file)
                        .param("name", "아리아")
                        .param("personaDescription", "중세 마법사")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("내 캐릭터 목록 조회 - 성공 (200)")
    void getMyCharacters_success() throws Exception {
        // 먼저 캐릭터 생성
        mockMvc.perform(multipart("/api/characters")
                .param("name", "아리아")
                .param("personaDescription", "중세 마법사")
                .header("Authorization", "Bearer " + accessToken));

        mockMvc.perform(get("/api/characters")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("캐릭터 상세 조회 - 성공 (200)")
    void getCharacter_success() throws Exception {
        // 캐릭터 생성 후 ID 추출
        String createResponse = mockMvc.perform(multipart("/api/characters")
                        .param("name", "아리아")
                        .param("personaDescription", "중세 마법사")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String characterId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        mockMvc.perform(get("/api/characters/" + characterId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("아리아"));
    }

    @Test
    @DisplayName("캐릭터 삭제 - 성공 (204)")
    void deleteCharacter_success() throws Exception {
        String createResponse = mockMvc.perform(multipart("/api/characters")
                        .param("name", "아리아")
                        .param("personaDescription", "중세 마법사")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String characterId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        mockMvc.perform(delete("/api/characters/" + characterId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("캐릭터 삭제 후 조회 - 404 반환")
    void getCharacter_afterDelete_returns404() throws Exception {
        String createResponse = mockMvc.perform(multipart("/api/characters")
                        .param("name", "아리아")
                        .param("personaDescription", "중세 마법사")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String characterId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.id");

        mockMvc.perform(delete("/api/characters/" + characterId)
                .header("Authorization", "Bearer " + accessToken));

        mockMvc.perform(get("/api/characters/" + characterId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }
}
