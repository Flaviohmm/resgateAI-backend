package br.com.resgateai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Cliente para a API de Mensagens da Anthropic (Claude) — provedor primário de IA
 * do app. A Anthropic não tem um modo JSON forçado como o {@code response_format}
 * da OpenAI; a saída estruturada depende só da instrução "responda SOMENTE em
 * JSON" já presente em todo prompt de sistema deste app, então {@link #complete}
 * e {@link #completeJson} chamam o mesmo endpoint.
 */
@Service
public class AnthropicClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 1024;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public AnthropicClient(
            @Value("${anthropic.api-key:}") String apiKey,
            @Value("${anthropic.model:claude-haiku-4-5}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String complete(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt);
    }

    public String completeJson(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt);
    }

    private String chat(String systemPrompt, String userPrompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY não configurada");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        MessagesResponse response = restClient.post()
                .uri("/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .body(body)
                .retrieve()
                .body(MessagesResponse.class);

        if (response == null || response.content() == null || response.content().isEmpty()) {
            throw new IllegalStateException("Resposta vazia da Anthropic");
        }
        return response.content().get(0).text();
    }

    private record MessagesResponse(List<Block> content) {
        private record Block(String text) {
        }
    }
}
