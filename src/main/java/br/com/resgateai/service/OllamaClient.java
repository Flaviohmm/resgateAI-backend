package br.com.resgateai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente para um servidor Ollama local — alternativa de contingência à OpenAI
 * (PRD §11). Usa o endpoint /api/chat com {@code format=json} para saída estruturada.
 * Configurável por variável de ambiente (OLLAMA_BASE_URL, OLLAMA_MODEL).
 */
@Service
public class OllamaClient {

    private final RestClient restClient;
    private final String model;

    public OllamaClient(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:llama3.1}") String model
    ) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public String complete(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, false);
    }

    public String completeJson(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, true);
    }

    private String chat(String systemPrompt, String userPrompt, boolean jsonMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        if (jsonMode) {
            body.put("format", "json");
        }

        OllamaChatResponse response = restClient.post()
                .uri("/api/chat")
                .body(body)
                .retrieve()
                .body(OllamaChatResponse.class);

        if (response == null || response.message() == null || response.message().content() == null) {
            throw new IllegalStateException("Resposta vazia do Ollama");
        }
        return response.message().content();
    }

    private record OllamaChatResponse(Message message) {
        private record Message(String content) {
        }
    }
}
