package br.com.resgateai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Cliente do bot do Telegram para o envio real da campanha (Arquitetura §3.5) —
 * o momento que separa a demonstração de um mockup, com a mensagem chegando ao
 * vivo no celular do telão. Token e chat_id configurados via BotFather.
 */
@Service
public class TelegramClient {

    private final RestClient restClient;
    private final String botToken;
    private final String chatId;

    public TelegramClient(
            @Value("${telegram.bot-token:}") String botToken,
            @Value("${telegram.chat-id:}") String chatId,
            @Value("${telegram.base-url:https://api.telegram.org}") String baseUrl
    ) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
    }

    public void sendMessage(String texto) {
        if (!isConfigured()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID não configurados");
        }

        restClient.post()
                .uri("/bot" + botToken + "/sendMessage")
                .body(Map.of("chat_id", chatId, "text", texto))
                .retrieve()
                .toBodilessEntity();
    }
}
