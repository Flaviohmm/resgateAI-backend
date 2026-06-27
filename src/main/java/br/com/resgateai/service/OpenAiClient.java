package br.com.resgateai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiClient(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.model}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Chat completion em texto livre. Usada para textos curtos (ex.: racional do
     * loop de aprendizado).
     */
    public String complete(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, false);
    }

    /**
     * Chat completion forçando saída em JSON ({@code response_format=json_object}).
     * Usada quando a resposta precisa ser mapeada para a interface (campanha, ficha).
     */
    public String completeJson(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, true);
    }

    private String chat(String systemPrompt, String userPrompt, boolean jsonMode) {
        if (!isConfigured()) {
            throw new IllegalStateException("OPENAI_API_KEY não configurada");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        ChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("Resposta vazia da OpenAI");
        }
        return response.choices().get(0).message().content();
    }

    private record ChatCompletionResponse(List<Choice> choices) {
        private record Choice(Message message) {
            private record Message(String content) {
            }
        }
    }
}
