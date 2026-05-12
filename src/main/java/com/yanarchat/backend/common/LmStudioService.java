package com.yanarchat.backend.common;

import com.yanarchat.backend.character.CharacterDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
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

    public Flux<String> streamChat(String systemPrompt, List<Map<String, String>> messages) {
        List<Map<String, Object>> allMessages = new ArrayList<>();
        allMessages.add(Map.of("role", "system", "content", systemPrompt));
        for (Map<String, String> m : messages) {
            allMessages.add(Map.of("role", m.get("role"), "content", m.get("content")));
        }

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "stream", true,
                    "temperature", 0.8,
                    "messages", allMessages
            ));
        } catch (Exception e) {
            return Flux.error(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "요청 생성에 실패했습니다."));
        }

        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .onErrorMap(WebClientException.class,
                        e -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "LM Studio 연결에 실패했습니다."))
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .flatMap(chunk -> Flux.fromArray(chunk.split("\n")))
                .map(String::trim)
                .filter(line -> line.startsWith("data: "))
                .map(line -> line.substring(6).trim())
                .filter(data -> !"[DONE]".equals(data))
                .mapNotNull(this::extractTokenFromChunk)
                .filter(token -> !token.isEmpty());
    }

    public String extractMemory(String userMessage, String assistantMessage) {
        String conversation = "사용자: " + userMessage + "\nAI: " + assistantMessage;
        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", 0.3,
                    "messages", List.of(
                            Map.of("role", "system",
                                    "content", "You are a memory extraction assistant. Analyze the given conversation and determine if there is important factual information about the user that should be remembered for future conversations (e.g., name, preferences, job, personal facts). If found, summarize it concisely in one sentence in Korean. If there is nothing important to remember, respond with exactly 'NONE'."),
                            Map.of("role", "user", "content", conversation + "\n\n중요한 정보가 있나요?")
                    )
            ));
        } catch (Exception e) {
            return null;
        }

        try {
            String responseJson = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return parseSimpleResponse(responseJson);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractTokenFromChunk(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode content = root.path("choices").get(0).path("delta").path("content");
            if (content.isMissingNode() || content.isNull()) return "";
            String text = content.textValue();
            return text != null ? text : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String parseSimpleResponse(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String content = root.path("choices").get(0).path("message").path("content").textValue();
            return content != null ? content.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String textOf(JsonNode node, String field) {
        String val = node.path(field).textValue();
        return val != null ? val : "";
    }
}
