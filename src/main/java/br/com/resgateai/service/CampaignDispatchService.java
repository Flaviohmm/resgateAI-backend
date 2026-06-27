package br.com.resgateai.service;

import br.com.resgateai.dto.response.DispatchResponse;
import br.com.resgateai.entity.Campanha;
import br.com.resgateai.repository.CampanhaRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Envio real da campanha via Telegram (Arquitetura §3.5). Idempotente: se a
 * campanha já foi enviada, não dispara de novo — apenas devolve o status e o
 * horário original, mesmo padrão de upsert idempotente usado em Recomendacao/Campanha.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignDispatchService {

    private final CampanhaRepository campanhaRepository;
    private final TelegramClient telegramClient;
    private final SustainabilityService sustainabilityService;

    @Transactional
    public DispatchResponse enviar(Long campanhaId) {
        Campanha campanha = campanhaRepository.findById(campanhaId)
                .orElseThrow(() -> new EntityNotFoundException("Campanha não encontrada: " + campanhaId));

        if (campanha.getEnviadaEm() == null) {
            telegramClient.sendMessage(montarMensagem(campanha));
            campanha.setEnviadaEm(LocalDateTime.now());
            campanha = campanhaRepository.save(campanha);
            sustainabilityService.registrarImpacto(campanha.getRecomendacao());
        } else {
            log.info("Campanha {} já havia sido enviada em {}, não reenviando", campanhaId, campanha.getEnviadaEm());
        }

        return DispatchResponse.builder()
                .status("enviada")
                .enviadaEm(campanha.getEnviadaEm())
                .build();
    }

    private String montarMensagem(Campanha campanha) {
        return """
                📢 Campanha Resgate AI

                💬 WhatsApp:
                %s

                📸 Story:
                %s

                🏷️ Etiqueta:
                %s""".formatted(
                campanha.getCanalWhatsapp(),
                campanha.getCanalStory(),
                campanha.getCanalEtiqueta());
    }
}
