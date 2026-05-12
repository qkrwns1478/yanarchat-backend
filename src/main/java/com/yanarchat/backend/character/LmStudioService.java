package com.yanarchat.backend.character;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class LmStudioService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${lmstudio.model:local-model}")
    private String model;

    public LmStudioService(
            @Value("${lmstudio.base-url:http://localhost:1234/v1}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    public CharacterDto.PersonaAttributes generatePersona(String combinedText) {
        String prompt = buildPrompt(combinedText);
        String requestBody = buildRequestBody(prompt);

        String responseJson;
        try {
            responseJson = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (WebClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LM Studio 연결에 실패했습니다.");
        }

        return parsePersonaResponse(responseJson);
    }

    private String buildPrompt(String combinedText) {
        return """
                다음 캐릭터 설명을 바탕으로 AI 채팅 캐릭터의 페르소나 속성을 생성해주세요.

                캐릭터 설명:
                %s

                반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 포함하지 마세요:
                {
                  "speechStyle": "캐릭터의 말투 설명",
                  "personality": "캐릭터의 성격 설명",
                  "traits": ["특징1", "특징2", "특징3"],
                  "background": "캐릭터의 배경 스토리",
                  "systemPrompt": "이 캐릭터로 롤플레이하기 위한 영문 시스템 프롬프트"
                }
                """.formatted(combinedText);
    }

    private String buildRequestBody(String userPrompt) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0.7,
                    "messages", List.of(
                            Map.of("role", "system",
                                    "content", "You are a creative AI assistant that generates character personas. Always respond with valid JSON only."),
                            Map.of("role", "user", "content", userPrompt)
                    )
            ));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "요청 생성에 실패했습니다.");
        }
    }

    private CharacterDto.PersonaAttributes parsePersonaResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String content = root.path("choices").get(0).path("message").path("content").textValue();

            if (content == null) {
                throw new IllegalStateException("LM Studio 응답 content가 비어 있습니다.");
            }

            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            JsonNode persona = objectMapper.readTree(content);

            List<String> traits = new ArrayList<>();
            JsonNode traitsNode = persona.path("traits");
            if (traitsNode.isArray()) {
                for (JsonNode t : traitsNode) {
                    String val = t.textValue();
                    if (val != null) traits.add(val);
                }
            }

            return new CharacterDto.PersonaAttributes(
                    textOf(persona, "speechStyle"),
                    textOf(persona, "personality"),
                    traits,
                    textOf(persona, "background"),
                    textOf(persona, "systemPrompt")
            );
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LM Studio 응답 파싱에 실패했습니다.");
        }
    }

    private String textOf(JsonNode node, String field) {
        String val = node.path(field).textValue();
        return val != null ? val : "";
    }
}
