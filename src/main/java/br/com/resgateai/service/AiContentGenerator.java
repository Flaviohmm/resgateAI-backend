package br.com.resgateai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Orquestra a geração de conteúdo pela IA, escolhendo o provedor primário por
 * configuração ({@code ai.provider}: anthropic | openai | ollama) e, opcionalmente,
 * caindo para os demais provedores (na ordem {@link #ORDEM_PADRAO}) se o primário
 * falhar ({@code ai.fallback-enabled}).
 *
 * A decisão de rota/economia continua 100% determinística no backend; esta camada
 * só gera texto. Se todos os provedores falharem, lança exceção — quem chama
 * (ex.: CampaignAIService) decide o fallback determinístico de conteúdo.
 */
@Service
@Slf4j
public class AiContentGenerator {

    private static final List<String> ORDEM_PADRAO = List.of("anthropic", "openai", "ollama");

    private final AnthropicClient anthropicClient;
    private final OpenAiClient openAiClient;
    private final OllamaClient ollamaClient;
    private final String provider;
    private final boolean fallbackEnabled;

    public AiContentGenerator(
            AnthropicClient anthropicClient,
            OpenAiClient openAiClient,
            OllamaClient ollamaClient,
            @Value("${ai.provider:anthropic}") String provider,
            @Value("${ai.fallback-enabled:true}") boolean fallbackEnabled
    ) {
        this.anthropicClient = anthropicClient;
        this.openAiClient = openAiClient;
        this.ollamaClient = ollamaClient;
        this.provider = provider == null ? "anthropic" : provider.trim().toLowerCase();
        this.fallbackEnabled = fallbackEnabled;
    }

    /**
     * Gera conteúdo JSON a partir dos prompts (uso: campanha, ficha de transformação).
     *
     * @throws IllegalStateException se nenhum provedor disponível produzir resposta
     */
    public String generateJson(String systemPrompt, String userPrompt) {
        Map<String, Supplier<String>> porNome = new LinkedHashMap<>();
        porNome.put("anthropic", () -> anthropicClient.completeJson(systemPrompt, userPrompt));
        porNome.put("openai", () -> openAiClient.completeJson(systemPrompt, userPrompt));
        porNome.put("ollama", () -> ollamaClient.completeJson(systemPrompt, userPrompt));
        return executar(montarCadeia(porNome));
    }

    /**
     * Gera texto livre a partir dos prompts (uso: racional do loop de aprendizado).
     *
     * @throws IllegalStateException se nenhum provedor disponível produzir resposta
     */
    public String generateText(String systemPrompt, String userPrompt) {
        Map<String, Supplier<String>> porNome = new LinkedHashMap<>();
        porNome.put("anthropic", () -> anthropicClient.complete(systemPrompt, userPrompt));
        porNome.put("openai", () -> openAiClient.complete(systemPrompt, userPrompt));
        porNome.put("ollama", () -> ollamaClient.complete(systemPrompt, userPrompt));
        return executar(montarCadeia(porNome));
    }

    private String executar(List<Tentativa> chain) {
        IllegalStateException ultimaFalha = null;

        for (Tentativa tentativa : chain) {
            try {
                return tentativa.acao().get();
            } catch (Exception e) {
                log.warn("Provedor de IA '{}' falhou: {}", tentativa.nome(), e.getMessage());
                ultimaFalha = new IllegalStateException(
                        "Provedor de IA '" + tentativa.nome() + "' falhou", e);
            }
        }

        throw ultimaFalha != null
                ? ultimaFalha
                : new IllegalStateException("Nenhum provedor de IA configurado");
    }

    private List<Tentativa> montarCadeia(Map<String, Supplier<String>> porNome) {
        String primario = porNome.containsKey(provider) ? provider : "anthropic";

        List<Tentativa> chain = new ArrayList<>();
        chain.add(new Tentativa(primario, porNome.get(primario)));
        if (fallbackEnabled) {
            for (String nome : ORDEM_PADRAO) {
                if (!nome.equals(primario)) {
                    chain.add(new Tentativa(nome, porNome.get(nome)));
                }
            }
        }
        return chain;
    }

    private record Tentativa(String nome, Supplier<String> acao) {
    }
}
