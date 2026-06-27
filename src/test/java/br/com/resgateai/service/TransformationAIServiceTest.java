package br.com.resgateai.service;

import br.com.resgateai.dto.response.TransformacaoResponse;
import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Produto;
import br.com.resgateai.entity.ProdutoTransformado;
import br.com.resgateai.entity.Recomendacao;
import br.com.resgateai.enums.CategoriaProduto;
import br.com.resgateai.enums.TipoRota;
import br.com.resgateai.repository.ProdutoTransformadoRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransformationAIServiceTest {

    @Mock
    private RecomendacaoRepository recomendacaoRepository;
    @Mock
    private ProdutoTransformadoRepository produtoTransformadoRepository;
    @Mock
    private AiContentGenerator aiContentGenerator;
    @Mock
    private SustainabilityService sustainabilityService;

    private final RiskScoringService riskScoringService = new RiskScoringService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private TransformationAIService service() {
        return new TransformationAIService(recomendacaoRepository, produtoTransformadoRepository,
                riskScoringService, aiContentGenerator, objectMapper, sustainabilityService);
    }

    private Recomendacao recomendacaoTransformar() {
        Produto produto = Produto.builder()
                .id(1L).nome("Banana Prata").categoria(CategoriaProduto.HORTIFRUTI)
                .custoUnit(new BigDecimal("0.80")).precoUnit(new BigDecimal("1.50"))
                .validadeDias(7).transformavel(true).transformDestino("Bolo de Banana").build();
        Lote lote = Lote.builder()
                .id(1L).produto(produto).quantidade(30)
                .dataRecebido(LocalDate.now().minusDays(5))
                .dataValidade(LocalDate.now().plusDays(1)).build();
        return Recomendacao.builder()
                .id(20L).lote(lote).rota(TipoRota.TRANSFORMAR).build();
    }

    @Test
    void geraFichaComJsonDaIaEPersiste() {
        Recomendacao rec = recomendacaoTransformar();
        when(recomendacaoRepository.findById(20L)).thenReturn(Optional.of(rec));
        when(produtoTransformadoRepository.findByRecomendacao(rec)).thenReturn(Optional.empty());
        when(aiContentGenerator.generateJson(anyString(), anyString())).thenReturn("""
                {"produto_novo":"Bolo de Banana","ingredientes":"Banana, farinha, açúcar",
                 "modo_preparo":"Misture e asse por 40 min","preco_sugerido":"R$ 12,90",
                 "argumento_venda":"Feito com fruta resgatada"}""");
        when(produtoTransformadoRepository.save(any(ProdutoTransformado.class))).thenAnswer(inv -> {
            ProdutoTransformado pt = inv.getArgument(0);
            pt.setId(77L);
            return pt;
        });

        TransformacaoResponse resp = service().transformar(20L);

        assertThat(resp.getProdutoTransformadoId()).isEqualTo(77L);
        assertThat(resp.getProdutoNovo()).isEqualTo("Bolo de Banana");
        assertThat(resp.getIngredientes()).contains("Banana");
        assertThat(resp.getPrecoSugerido()).isEqualByComparingTo("12.90");
        verify(sustainabilityService, times(1)).registrarImpacto(rec);
    }

    @Test
    void naoRegistraImpactoDeNovoQuandoFichaJaExistia() {
        Recomendacao rec = recomendacaoTransformar();
        when(recomendacaoRepository.findById(20L)).thenReturn(Optional.of(rec));
        when(produtoTransformadoRepository.findByRecomendacao(rec))
                .thenReturn(Optional.of(ProdutoTransformado.builder().id(77L).recomendacao(rec).build()));
        when(aiContentGenerator.generateJson(anyString(), anyString())).thenReturn("""
                {"produto_novo":"Bolo de Banana","ingredientes":"Banana, farinha, açúcar",
                 "modo_preparo":"Misture e asse por 40 min","preco_sugerido":"R$ 12,90",
                 "argumento_venda":"Feito com fruta resgatada"}""");
        when(produtoTransformadoRepository.save(any(ProdutoTransformado.class))).thenAnswer(inv -> inv.getArgument(0));

        service().transformar(20L);

        verify(sustainabilityService, never()).registrarImpacto(any());
    }

    @Test
    void parseiaPrecoComSeparadorDeMilharBrasileiro() {
        Recomendacao rec = recomendacaoTransformar();
        when(recomendacaoRepository.findById(20L)).thenReturn(Optional.of(rec));
        when(produtoTransformadoRepository.findByRecomendacao(rec)).thenReturn(Optional.empty());
        when(aiContentGenerator.generateJson(anyString(), anyString())).thenReturn("""
                {"produto_novo":"Cesta Premium","ingredientes":"x","modo_preparo":"y",
                 "preco_sugerido":"R$ 1.234,50","argumento_venda":"z"}""");
        when(produtoTransformadoRepository.save(any(ProdutoTransformado.class))).thenAnswer(inv -> inv.getArgument(0));

        TransformacaoResponse resp = service().transformar(20L);

        assertThat(resp.getPrecoSugerido()).isEqualByComparingTo("1234.50");
    }

    @Test
    void quandoIaFalhaUsaFallbackDeterministico() {
        Recomendacao rec = recomendacaoTransformar();
        when(recomendacaoRepository.findById(20L)).thenReturn(Optional.of(rec));
        when(produtoTransformadoRepository.findByRecomendacao(rec)).thenReturn(Optional.empty());
        when(aiContentGenerator.generateJson(anyString(), anyString()))
                .thenThrow(new IllegalStateException("sem provedor de IA"));
        when(produtoTransformadoRepository.save(any(ProdutoTransformado.class))).thenAnswer(inv -> inv.getArgument(0));

        TransformacaoResponse resp = service().transformar(20L);

        // fallback usa o destino sugerido e calcula preço a partir do markup (1.50 * 1.8 = 2.70)
        assertThat(resp.getProdutoNovo()).isEqualTo("Bolo de Banana");
        assertThat(resp.getIngredientes()).contains("Banana Prata");
        assertThat(resp.getModoPreparo()).isNotBlank();
        assertThat(resp.getArgumentoVenda()).isNotBlank();
        assertThat(resp.getPrecoSugerido()).isEqualByComparingTo("2.70");
    }

    @Test
    void rejeitaRotaQueNaoSejaTransformar() {
        Recomendacao rec = recomendacaoTransformar();
        rec.setRota(TipoRota.REMARCAR);
        when(recomendacaoRepository.findById(20L)).thenReturn(Optional.of(rec));

        assertThatThrownBy(() -> service().transformar(20L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TRANSFORMAR");
    }
}
