package br.com.resgateai.service;

import br.com.resgateai.dto.response.SugestaoPedidoResponse;
import br.com.resgateai.entity.HistoricoResgate;
import br.com.resgateai.entity.Produto;
import br.com.resgateai.repository.HistoricoResgateRepository;
import br.com.resgateai.repository.SugestaoPedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LearningLoopServiceTest {

    @Mock
    private HistoricoResgateRepository historicoResgateRepository;
    @Mock
    private SugestaoPedidoRepository sugestaoPedidoRepository;
    @Mock
    private AiContentGenerator aiContentGenerator;

    private final LocalDate semanaCorrente = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    private LearningLoopService service() {
        return new LearningLoopService(historicoResgateRepository, sugestaoPedidoRepository, aiContentGenerator);
    }

    private HistoricoResgate registro(Produto produto, LocalDate semana) {
        return HistoricoResgate.builder().produto(produto).semanaRef(semana).quantidadeEmRisco(10).build();
    }

    /**
     * A semana corrente (em andamento) não pode contar: garante que a sugestão é
     * determinística mesmo depois de a fila ser aberta (que grava a semana corrente).
     */
    @Test
    void ignoraSemanaCorrenteEContaApenasSemanasCompletas() {
        Produto iogurte = Produto.builder().id(1L).nome("Iogurte Morango 170g").build();
        List<HistoricoResgate> historico = new ArrayList<>(List.of(
                registro(iogurte, semanaCorrente),                 // em andamento -> ignorada
                registro(iogurte, semanaCorrente.minusWeeks(1)),
                registro(iogurte, semanaCorrente.minusWeeks(2)),
                registro(iogurte, semanaCorrente.minusWeeks(3))));
        when(historicoResgateRepository.findBySemanaRefGreaterThanEqual(any())).thenReturn(historico);
        when(sugestaoPedidoRepository.findTopByProdutoOrderByGeradoEmDesc(iogurte)).thenReturn(Optional.empty());
        when(aiContentGenerator.generateText(anyString(), anyString())).thenThrow(new IllegalStateException("sem IA"));

        List<SugestaoPedidoResponse> sugestoes = service().gerarSugestoesPedido();

        assertThat(sugestoes).hasSize(1);
        assertThat(sugestoes.get(0).getOcorrenciasRisco()).isEqualTo(3);
        assertThat(sugestoes.get(0).getAjustePct()).isEqualTo(-30);
    }

    /**
     * Repetir a semana corrente (várias aberturas da fila) não muda nada: ela já é
     * ignorada e as demais semanas são contadas por valor distinto.
     */
    @Test
    void aberturasRepetidasDaFilaNaoAlteramOAjuste() {
        Produto pao = Produto.builder().id(7L).nome("Pão de Forma 500g").build();
        List<HistoricoResgate> historico = new ArrayList<>(List.of(
                registro(pao, semanaCorrente),
                registro(pao, semanaCorrente),                     // duplicada, mesma semana corrente
                registro(pao, semanaCorrente.minusWeeks(1)),
                registro(pao, semanaCorrente.minusWeeks(3))));
        when(historicoResgateRepository.findBySemanaRefGreaterThanEqual(any())).thenReturn(historico);
        when(sugestaoPedidoRepository.findTopByProdutoOrderByGeradoEmDesc(pao)).thenReturn(Optional.empty());
        when(aiContentGenerator.generateText(anyString(), anyString())).thenThrow(new IllegalStateException("sem IA"));

        List<SugestaoPedidoResponse> sugestoes = service().gerarSugestoesPedido();

        assertThat(sugestoes).hasSize(1);
        assertThat(sugestoes.get(0).getOcorrenciasRisco()).isEqualTo(2);
        assertThat(sugestoes.get(0).getAjustePct()).isEqualTo(-20);
    }

    /**
     * Menos de 2 semanas completas em risco -> sem sugestão (não mexe no pedido).
     */
    @Test
    void abaixoDoMinimoNaoGeraSugestao() {
        Produto leite = Produto.builder().id(2L).nome("Leite Integral 1L").build();
        List<HistoricoResgate> historico = new ArrayList<>(List.of(
                registro(leite, semanaCorrente),                   // ignorada
                registro(leite, semanaCorrente.minusWeeks(1))));   // só 1 semana completa
        when(historicoResgateRepository.findBySemanaRefGreaterThanEqual(any())).thenReturn(historico);

        List<SugestaoPedidoResponse> sugestoes = service().gerarSugestoesPedido();

        assertThat(sugestoes).isEmpty();
    }
}
