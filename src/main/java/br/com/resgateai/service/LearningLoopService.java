package br.com.resgateai.service;

import br.com.resgateai.dto.response.SugestaoPedidoResponse;
import br.com.resgateai.entity.HistoricoResgate;
import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Produto;
import br.com.resgateai.entity.SugestaoPedido;
import br.com.resgateai.enums.NivelRisco;
import br.com.resgateai.repository.HistoricoResgateRepository;
import br.com.resgateai.repository.SugestaoPedidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Heurística estatística simples (Arquitetura §3.6), não um modelo de previsão de
 * demanda: conta em quantas das últimas 4 semanas um produto apareceu em risco e
 * traduz isso num ajuste percentual do próximo pedido.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LearningLoopService {

    private static final int JANELA_SEMANAS = 4;
    private static final int OCORRENCIAS_MINIMAS = 2;
    private static final int AJUSTE_MAXIMO_PCT = 40;

    private final HistoricoResgateRepository historicoResgateRepository;
    private final SugestaoPedidoRepository sugestaoPedidoRepository;
    private final AiContentGenerator aiContentGenerator;

    private LocalDate semanaAtual() {
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Chamado pela fila de risco (GET /fila) para cada lote com nível != BAIXO.
     * Idempotente por (produto, semana): polls repetidos na mesma semana não duplicam.
     */
    public void registrarEntradaNaFila(Lote lote, NivelRisco nivel) {
        if (nivel == NivelRisco.BAIXO) {
            return;
        }
        Produto produto = lote.getProduto();
        LocalDate semanaRef = semanaAtual();
        if (historicoResgateRepository.existsByProdutoAndSemanaRef(produto, semanaRef)) {
            return;
        }
        historicoResgateRepository.save(HistoricoResgate.builder()
                .produto(produto)
                .lote(lote)
                .quantidadeEmRisco(lote.getQuantidade())
                .semanaRef(semanaRef)
                .build());
    }

    public List<SugestaoPedidoResponse> gerarSugestoesPedido() {
        LocalDate semanaCorrente = semanaAtual();
        LocalDate desde = semanaCorrente.minusWeeks(JANELA_SEMANAS);
        Map<Produto, List<HistoricoResgate>> porProduto = historicoResgateRepository
                .findBySemanaRefGreaterThanEqual(desde)
                .stream()
                // Conta só semanas COMPLETAS: a semana corrente ainda está em andamento,
                // então é ignorada. Sem isso, abrir a fila (que grava a semana corrente em
                // historico_resgate) inflaria a contagem e tornaria a sugestão não
                // determinística — quebrando a "demonstração determinística" do PRD §8.1.
                .filter(h -> h.getSemanaRef().isBefore(semanaCorrente))
                .collect(Collectors.groupingBy(HistoricoResgate::getProduto));

        return porProduto.entrySet().stream()
                .map(entry -> calcularSugestao(entry.getKey(), entry.getValue()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private SugestaoPedidoResponse calcularSugestao(Produto produto, List<HistoricoResgate> historico) {
        long ocorrencias = historico.stream()
                .map(HistoricoResgate::getSemanaRef)
                .distinct()
                .count();

        if (ocorrencias < OCORRENCIAS_MINIMAS) {
            return null;
        }

        int ajustePct = -(int) ((ocorrencias * AJUSTE_MAXIMO_PCT) / JANELA_SEMANAS);
        String racional = gerarRacional(produto, (int) ocorrencias, ajustePct);

        SugestaoPedido sugestao = sugestaoPedidoRepository.findTopByProdutoOrderByGeradoEmDesc(produto)
                .orElseGet(() -> SugestaoPedido.builder().produto(produto).build());
        sugestao.setOcorrenciasRisco((int) ocorrencias);
        sugestao.setAjustePct(ajustePct);
        sugestao.setRacional(racional);
        sugestaoPedidoRepository.save(sugestao);

        return SugestaoPedidoResponse.builder()
                .produtoId(produto.getId())
                .nome(produto.getNome())
                .ocorrenciasRisco((int) ocorrencias)
                .ajustePct(ajustePct)
                .racional(racional)
                .build();
    }

    private String gerarRacional(Produto produto, int ocorrencias, int ajustePct) {
        try {
            String sistema = "Você explica, em uma frase direta, por que o próximo pedido de "
                    + "um produto está sendo reduzido. Use o histórico de resgate como base. "
                    + "Tom: conselheiro prático, sem rodeios.";
            String usuario = "Produto: %s. Apareceu em risco de vencimento em %d das últimas 4 semanas. "
                    .formatted(produto.getNome(), ocorrencias)
                    + "Ajuste sugerido: reduzir o próximo pedido em %d%%.".formatted(Math.abs(ajustePct));
            return aiContentGenerator.generateText(sistema, usuario).trim();
        } catch (Exception e) {
            log.warn("Falha ao gerar racional via IA para {}, usando fallback determinístico", produto.getNome(), e);
            return "%s: sobrou em %d das últimas 4 semanas. Sugerimos reduzir o próximo pedido em %d%%."
                    .formatted(produto.getNome(), ocorrencias, Math.abs(ajustePct));
        }
    }
}
