package com.yanarchat.backend.character;

import com.yanarchat.backend.auth.AuthDto;
import com.yanarchat.backend.auth.AuthService;
import com.yanarchat.backend.common.LmStudioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * LM Studio E2E 테스트
 * LM Studio가 localhost:1234에서 실행 중일 때만 수동으로 실행할 것
 *
 * 실행 방법: ./gradlew e2eTest
 */
@Tag("e2e")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CharacterE2ETest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private LmStudioService lmStudioService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        AuthDto.TokenResponse tokens = authService.signup(new AuthDto.SignupRequest(
                "e2e-" + UUID.randomUUID() + "@example.com", "e2eTester", "password123!"));
        accessToken = tokens.accessToken();
    }

    @Test
    @DisplayName("LM Studio 연결 확인 - 페르소나 속성 생성")
    void lmStudio_generatePersona_success() {
        String combinedText = "중세 시대 왕국의 수석 마법사. 냉정하지만 속으로는 따뜻한 성격을 가졌다. 오랜 시간 홀로 연구에 매진해왔다.";

        CharacterDto.PersonaAttributes persona = lmStudioService.generatePersona(combinedText);

        assertNotNull(persona);
        assertFalse(persona.speechStyle().isBlank(), "speechStyle이 비어 있음");
        assertFalse(persona.personality().isBlank(), "personality가 비어 있음");
        assertFalse(persona.traits().isEmpty(), "traits가 비어 있음");
        assertFalse(persona.background().isBlank(), "background가 비어 있음");
        assertFalse(persona.systemPrompt().isBlank(), "systemPrompt가 비어 있음");

        System.out.println("=== LM Studio 페르소나 생성 결과 ===");
        System.out.println("speechStyle : " + persona.speechStyle());
        System.out.println("personality : " + persona.personality());
        System.out.println("traits      : " + persona.traits());
        System.out.println("background  : " + persona.background());
        System.out.println("systemPrompt: " + persona.systemPrompt());
    }

    @Test
    @DisplayName("캐릭터 전체 생성 플로우 - 파일 없이 LM Studio 실제 호출")
    void createCharacter_fullFlow_withoutFiles() throws Exception {
        mockMvc.perform(multipart("/api/characters")
                        .param("name", "아리아")
                        .param("personaDescription", "중세 시대 왕국의 수석 마법사. 냉정하지만 속으로는 따뜻한 성격. 오랜 시간 홀로 연구에 매진해왔다.")
                        .header("Authorization", "Bearer " + accessToken))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("아리아"))
                .andExpect(jsonPath("$.speechStyle").isNotEmpty())
                .andExpect(jsonPath("$.personality").isNotEmpty())
                .andExpect(jsonPath("$.traits").isArray())
                .andExpect(jsonPath("$.background").isNotEmpty())
                .andExpect(jsonPath("$.systemPrompt").isNotEmpty());
    }

    @Test
    @DisplayName("캐릭터 전체 생성 플로우 - TXT 파일 첨부 후 LM Studio 실제 호출")
    void createCharacter_fullFlow_withTxtFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "aria_detail.txt", MediaType.TEXT_PLAIN_VALUE,
                """
                [상세 설정]
                - 나이: 30세
                - 특기: 화염 마법, 바람 마법
                - 약점: 물에 약함
                - 좋아하는 것: 고서, 밤하늘
                - 싫어하는 것: 거짓말, 배신
                """.getBytes()
        );

        mockMvc.perform(multipart("/api/characters")
                        .file(file)
                        .param("name", "아리아")
                        .param("personaDescription", "중세 시대 왕국의 수석 마법사")
                        .header("Authorization", "Bearer " + accessToken))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.files[0].originalName").value("aria_detail.txt"))
                .andExpect(jsonPath("$.files[0].fileType").value("TXT"));
    }
}
