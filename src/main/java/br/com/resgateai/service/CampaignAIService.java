package br.com.resgateai.service;

import br.com.resgateai.dto.response.CampaignResponse;
import br.com.resgateai.entity.Campanha;
import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Produto;
import br.com.resgateai.entity.Recomendacao;
import br.com.resgateai.enums.TipoRota;
import br.com.resgateai.repository.CampanhaRepository;
import br.com.resgateai.repository.RecomendacaoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Geração de campanha multicanal para a rota REMARCAR (Arquitetura §3.4 / §4.1).
 * A IA gera apenas o texto (whatsapp/story/etiqueta); a decisão de remarcar e o
 * desconto ótimo já vêm prontos da Recomendação determinística (Story 2.3).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignAIService {

    private static final String SISTEMA = """
            Você é redator de varejo. Gere conteúdo curto e persuasivo para escoar \
            um produto perto do vencimento. Responda SOMENTE em JSON, sem markdown, \
            no formato:
            { "whatsapp": "...", "story": "...", "etiqueta": "..." }""";

    private final RecomendacaoRepository recomendacaoRepository;
    private final CampanhaRepository campanhaRepository;
    private final RiskScoringService riskScoringService;
    private final AiContentGenerator aiContentGenerator;
    private final ObjectMapper objectMapper;

    @Transactional
    public CampaignResponse gerarCampanha(Long recomendacaoId) {
        Recomendacao rec = recomendacaoRepository.findById(recomendacaoId)
                .orElseThrow(() -> new EntityNotFoundException("Recomendação não encontrada: " + recomendacaoId));

        if (rec.getRota() != TipoRota.REMARCAR) {
            throw new IllegalStateException(
                    "Geração de campanha só se aplica à rota REMARCAR; rota atual: " + rec.getRota());
        }

        Lote lote = rec.getLote();
        Produto produto = lote.getProduto();
        int desconto = rec.getDescontoPct() != null ? rec.getDescontoPct() : 0;
        long dias = Math.max(0, riskScoringService.diasParaVencer(lote));

        ConteudoCampanha conteudo = gerarConteudo(produto, desconto, dias);

        Campanha campanha = campanhaRepository.findByRecomendacao(rec)
                .orElseGet(() -> Campanha.builder().recomendacao(rec).build());
        campanha.setCanalWhatsapp(conteudo.whatsapp());
        campanha.setCanalStory(conteudo.story());
        campanha.setCanalEtiqueta(conteudo.etiqueta());
        campanha = campanhaRepository.save(campanha);

        return CampaignResponse.builder()
                .campanhaId(campanha.getId())
                .whatsapp(conteudo.whatsapp())
                .story(conteudo.story())
                .etiqueta(conteudo.etiqueta())
                .enviada(campanha.getEnviadaEm() != null)
                .build();
    }

    private ConteudoCampanha gerarConteudo(Produto produto, int desconto, long dias) {
        String usuario = ("Produto: %s. Desconto: %d%%. Validade: %d dias. Categoria: %s. "
                + "Tom: urgência amigável, foco em economia para o cliente. "
                + "Etiqueta com no máximo 8 palavras.")
                .formatted(produto.getNome(), desconto, dias, produto.getCategoria());

        try {
            String json = aiContentGenerator.generateJson(SISTEMA, usuario);
            JsonNode node = objectMapper.readTree(json);
            String whatsapp = texto(node, "whatsapp");
            String story = texto(node, "story");
            String etiqueta = texto(node, "etiqueta");

            if (whatsapp.isBlank() && story.isBlank() && etiqueta.isBlank()) {
                throw new IllegalStateException("IA retornou conteúdo vazio");
            }
            return new ConteudoCampanha(whatsapp, story, etiqueta);
        } catch (Exception e) {
            log.warn("Falha ao gerar campanha via IA para '{}', usando fallback determinístico",
                    produto.getNome(), e);
            return fallbackDeterministico(produto, desconto, dias);
        }
    }

    private ConteudoCampanha fallbackDeterministico(Produto produto, int desconto, long dias) {
        String nome = produto.getNome();
        String whatsapp = ("🔥 %s com %d%% OFF! Só faltam %d dia(s) — aproveite e economize de verdade. "
                + "Corra antes que acabe! 🛒")
                .formatted(nome, desconto, dias);
        String story = "Oferta relâmpago: %s com %d%% de desconto. É hoje, não perca!".formatted(nome, desconto);
        String etiqueta = "%s -%d%% — leve agora".formatted(nome, desconto);
        return new ConteudoCampanha(whatsapp, story, etiqueta);
    }

    private String texto(JsonNode node, String campo) {
        JsonNode valor = node.get(campo);
        return valor == null || valor.isNull() ? "" : valor.asText().trim();
    }

    private record ConteudoCampanha(String whatsapp, String story, String etiqueta) {
    }
}
