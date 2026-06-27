package br.com.resgateai.service;

import br.com.resgateai.dto.response.CampaignResponse;
import br.com.resgateai.entity.Campanha;
import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Produto;
import br.com.resgateai.entity.Recomendacao;
import br.com.resgateai.enums.CategoriaProduto;
import br.com.resgateai.enums.TipoRota;
import br.com.resgateai.repository.CampanhaRepository;
import br.com.resgateai.repository.RecomendacaoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignAIServiceTest {

    @Mock
    private RecomendacaoRepository recomendacaoRepository;
    @Mock
    private CampanhaRepository campanhaRepository;
    @Mock
    private AiContentGenerator aiContentGenerator;

    private final RiskScoringService riskScoringService = new RiskScoringService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private CampaignAIService service() {
        return new CampaignAIService(recomendacaoRepository, campanhaRepository,
                riskScoringService, aiContentGenerator, objectMapper);
    }

    private Recomendacao recomendacaoRemarcar() {
        Produto produto = Produto.builder()
                .id(1L).nome("Iogurte Morango 170g").categoria(CategoriaProduto.LATICINIO)
                .custoUnit(new BigDecimal("1.50")).precoUnit(new BigDecimal("2.10"))
                .validadeDias(30).transformavel(false).build();
        Lote lote = Lote.builder()
                .id(1L).produto(produto).quantidade(40)
                .dataRecebido(LocalDate.now().minusDays(28))
                .dataValidade(LocalDate.now().plusDays(2)).build();
        return Recomendacao.builder()
                .id(10L).lote(lote).rota(TipoRota.REMARCAR).descontoPct(30).build();
    }

    @Test
    void geraCampanhaComJsonDaIaEPersiste() {
        Recomendacao rec = recomendacaoRemarcar();
        when(recomendacaoRepository.findById(10L)).thenReturn(Optional.of(rec));
        when(campanhaRepository.findByRecomendacao(rec)).thenReturn(Optional.empty());
        when(aiContentGenerator.generateJson(anyString(), anyString())).thenReturn(
                "{\"whatsapp\":\"Promo de iogurte!\",\"story\":\"Corre!\",\"etiqueta\":\"Iogurte -30%\"}");
        when(campanhaRepository.save(any(Campanha.class))).thenAnswer(inv -> {
            Campanha c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });

        CampaignResponse resp = service().gerarCampanha(10L);

        assertThat(resp.getCampanhaId()).isEqualTo(99L);
        assertThat(resp.getWhatsapp()).isEqualTo("Promo de iogurte!");
        assertThat(resp.getStory()).isEqualTo("Corre!");
        assertThat(resp.getEtiqueta()).isEqualTo("Iogurte -30%");
        assertThat(resp.getEnviada()).isFalse();
    }

    @Test
    void quandoIaFalhaUsaFallbackDeterministico() {
        Recomendacao rec = recomendacaoRemarcar();
        when(recomendacaoRepository.findById(10L)).thenReturn(Optional.of(rec));
        when(campanhaRepository.findByRecomendacao(rec)).thenReturn(Optional.empty());
        when(aiContentGenerator.generateJson(anyString(), anyString()))
                .thenThrow(new IllegalStateException("sem provedor de IA"));
        when(campanhaRepository.save(any(Campanha.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse resp = service().gerarCampanha(10L);

        // o fallback ainda entrega conteúdo nos 3 canais, com nome e desconto reais
        assertThat(resp.getWhatsapp()).contains("Iogurte Morango 170g").contains("30%");
        assertThat(resp.getStory()).contains("Iogurte Morango 170g").contains("30%");
        assertThat(resp.getEtiqueta()).contains("Iogurte Morango 170g").contains("30%");
    }

    @Test
    void rejeitaRotaQueNaoSejaRemarcar() {
        Recomendacao rec = recomendacaoRemarcar();
        rec.setRota(TipoRota.DOAR);
        when(recomendacaoRepository.findById(10L)).thenReturn(Optional.of(rec));

        assertThatThrownBy(() -> service().gerarCampanha(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REMARCAR");
    }

    @Test
    void jsonComAspasEscapadasEAcentosEhParseadoCorretamente() {
        Recomendacao rec = recomendacaoRemarcar();
        when(recomendacaoRepository.findById(10L)).thenReturn(Optional.of(rec));
        when(campanhaRepository.findByRecomendacao(rec)).thenReturn(Optional.empty());
        when(aiContentGenerator.generateJson(anyString(), anyString())).thenReturn(
                "{\"whatsapp\":\"Promoção imperdível: \\\"corre\\\"!\",\"story\":\"Última chance\",\"etiqueta\":\"Leve já\"}");
        when(campanhaRepository.save(any(Campanha.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignResponse resp = service().gerarCampanha(10L);

        assertThat(resp.getWhatsapp()).isEqualTo("Promoção imperdível: \"corre\"!");
        assertThat(resp.getStory()).isEqualTo("Última chance");
    }
}
