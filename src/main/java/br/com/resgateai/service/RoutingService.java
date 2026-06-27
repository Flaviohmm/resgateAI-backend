package br.com.resgateai.service;

import br.com.resgateai.dto.response.RecommendationResponse;
import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Produto;
import br.com.resgateai.entity.Recomendacao;
import br.com.resgateai.entity.Volume;
import br.com.resgateai.enums.NivelRisco;
import br.com.resgateai.enums.TipoRota;
import br.com.resgateai.repository.LoteRepository;
import br.com.resgateai.repository.RecomendacaoRepository;
import br.com.resgateai.repository.VolumeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Motor de roteamento (Arquitetura §3.2) + economia (§3.3).
 *
 * <p>Toda a decisão é heurística e determinística no backend — a IA generativa entra
 * só na camada de conteúdo (campanha/ficha, Stories 3.1/4.1), nunca no cálculo. Por
 * isso o roteamento roda offline, rápido e previsível, e o {@code racional} aqui é um
 * texto-regra com os números reais, não uma chamada de modelo.
 *
 * <p>A árvore avalia as 5 rotas em precedência estrita (primeira que casa vence):
 * TRANSFORMAR → REMARCAR(crítico) → COMBINAR → REPOSICIONAR → DOAR → REMARCAR(padrão).
 */
@Service
@RequiredArgsConstructor
public class RoutingService {

    // --- Limiares da árvore de decisão (Arquitetura §2, nota de revisão de schema/seed) ---
    /** "quantidade alta" na regra REMARCAR. */
    private static final int QUANTIDADE_ALTA = 20;
    /** "giro baixo" na regra REPOSICIONAR: ainda gira, mas devagar. */
    private static final double GIRO_BAIXO_LIMITE = 1.0;

    // --- Parâmetros da economia de remarcação (§3.3) ---
    /** Quanto a velocidade de venda reage ao desconto. Com 2.5 o ótimo cai ~30% para itens limitados por velocidade. */
    private static final double SENSIBILIDADE = 2.5;
    /** Giro base (un./dia) quando o lote não tem sinal de volume — itens de REMARCAR no seed não têm. */
    private static final double BASE_GIRO_DEFAULT = 8.0;
    private static final double[] DESCONTOS = {0.10, 0.20, 0.30, 0.40, 0.50};
    private static final BigDecimal CUSTO_DESCARTE_FATOR = new BigDecimal("1.05"); // custo + 5% de descarte

    // --- Estimativas das rotas secundárias (refinadas depois: TRANSFORMAR pela IA na Story 4.1) ---
    /** Valor do produto transformado como múltiplo do custo dos insumos. */
    private static final BigDecimal TRANSFORM_MARKUP = new BigDecimal("1.8");
    /** Fração do estoque em risco que o combo escoa a preço cheio. */
    private static final BigDecimal COMBO_SELLTHROUGH = new BigDecimal("0.5");
    /** Fração escoada ao reposicionar em ponta de gôndola/checkout. */
    private static final BigDecimal REPO_SELLTHROUGH = new BigDecimal("0.4");
    /** Benefício fiscal estimado da doação como fração do custo. */
    private static final BigDecimal DOAR_FISCAL_PCT = new BigDecimal("0.30");

    private final LoteRepository loteRepository;
    private final RecomendacaoRepository recomendacaoRepository;
    private final VolumeRepository volumeRepository;
    private final RiskScoringService riskScoringService;

    @Transactional
    public RecommendationResponse recomendar(Long loteId) {
        Lote lote = loteRepository.findById(loteId)
                .orElseThrow(() -> new EntityNotFoundException("Lote não encontrado: " + loteId));

        TipoRota rota = decidirRota(lote);
        Economia eco = calcularEconomia(lote, rota);
        String racional = montarRacional(lote, rota, eco);

        Recomendacao rec = recomendacaoRepository.findByLote(lote)
                .orElseGet(() -> Recomendacao.builder().lote(lote).build());
        rec.setRota(rota);
        rec.setDescontoPct(eco.descontoPct());
        rec.setComboProduto(eco.comboProduto());
        rec.setReceitaRecuperada(eco.receitaRecuperada());
        rec.setPerdaEvitada(eco.perdaEvitada());
        rec.setRacional(racional);
        rec = recomendacaoRepository.save(rec);

        return RecommendationResponse.builder()
                .recomendacaoId(rec.getId())
                .rota(rota)
                .descontoPct(eco.descontoPct())
                .produtoComb(eco.comboProduto() != null ? eco.comboProduto().getNome() : null)
                .receitaRecuperada(eco.receitaRecuperada())
                .perdaEvitada(eco.perdaEvitada())
                .economiaProjetada(eco.economiaProjetada())
                .racional(racional)
                .build();
    }

    // ------------------------------------------------------------------
    // Árvore de decisão (§3.2) — precedência estrita, primeira que casa vence.
    // ------------------------------------------------------------------
    TipoRota decidirRota(Lote lote) {
        Produto produto = lote.getProduto();
        NivelRisco nivel = riskScoringService.calcularNivel(lote);
        long dias = riskScoringService.diasParaVencer(lote);

        if (Boolean.TRUE.equals(produto.getTransformavel())
                && (nivel == NivelRisco.CRITICO || nivel == NivelRisco.ALTO)) {
            return TipoRota.TRANSFORMAR;
        }
        if (nivel == NivelRisco.CRITICO && lote.getQuantidade() >= QUANTIDADE_ALTA) {
            return TipoRota.REMARCAR;
        }
        if (temComplementoEmEstoque(produto)) {
            return TipoRota.COMBINAR;
        }
        if (nivel == NivelRisco.MEDIO && giroBaixo(lote)) {
            return TipoRota.REPOSICIONAR;
        }
        if (dias <= 1 && semSaidaDeVenda(lote)) {
            return TipoRota.DOAR;
        }
        return TipoRota.REMARCAR; // padrão
    }

    /** Produto tem complemento natural definido E esse complemento está parado em estoque. */
    private boolean temComplementoEmEstoque(Produto produto) {
        Produto complemento = produto.getComboNatural();
        return complemento != null && !loteRepository.findByProduto(complemento).isEmpty();
    }

    /** Tem sinal de volume e gira, mas abaixo do limite (ainda há saída, só que lenta). */
    private boolean giroBaixo(Lote lote) {
        return volumeRepository.findByLote(lote)
                .map(Volume::getGiroEstoque)
                .filter(g -> g != null)
                .map(g -> g.doubleValue() > 0 && g.doubleValue() < GIRO_BAIXO_LIMITE)
                .orElse(false);
    }

    /** Nenhuma evidência de saída: sem registro de volume ou giro zerado. Distinto de "giro baixo". */
    private boolean semSaidaDeVenda(Lote lote) {
        return volumeRepository.findByLote(lote)
                .map(Volume::getGiroEstoque)
                .map(g -> g == null || g.doubleValue() <= 0)
                .orElse(true);
    }

    // ------------------------------------------------------------------
    // Economia por rota. REMARCAR usa a heurística completa do §3.3; as demais
    // usam estimativas documentadas (TRANSFORMAR é refinada pela IA na Story 4.1).
    // ------------------------------------------------------------------
    private Economia calcularEconomia(Lote lote, TipoRota rota) {
        return switch (rota) {
            case REMARCAR -> economiaRemarcar(lote);
            case TRANSFORMAR -> economiaTransformar(lote);
            case COMBINAR -> economiaCombinar(lote);
            case REPOSICIONAR -> economiaReposicionar(lote);
            case DOAR -> economiaDoar(lote);
        };
    }

    /** §3.3: varre os descontos, escolhe o que maximiza a receita (empate → menor desconto, melhor margem). */
    private Economia economiaRemarcar(Lote lote) {
        Produto produto = lote.getProduto();
        double precoUnit = produto.getPrecoUnit().doubleValue();
        int quantidade = lote.getQuantidade();
        long dias = Math.max(0, riskScoringService.diasParaVencer(lote));
        double baseGiro = baseGiro(lote);

        double melhorReceita = -1;
        double melhorDesconto = DESCONTOS[0];
        double melhorUnidades = 0;
        for (double d : DESCONTOS) {
            double velocidade = baseGiro * (1 + d * SENSIBILIDADE);
            double unidadesVend = Math.min(quantidade, velocidade * dias);
            double receita = precoUnit * (1 - d) * unidadesVend;
            if (receita > melhorReceita) {
                melhorReceita = receita;
                melhorDesconto = d;
                melhorUnidades = unidadesVend;
            }
        }

        BigDecimal receitaRecuperada = bd(melhorReceita);
        BigDecimal perdaEvitada = bd(produto.getCustoUnit().doubleValue() * melhorUnidades)
                .multiply(CUSTO_DESCARTE_FATOR);
        return new Economia(
                (int) Math.round(melhorDesconto * 100),
                null,
                receitaRecuperada,
                escala(perdaEvitada),
                escala(receitaRecuperada.add(perdaEvitada)),
                (int) Math.round(melhorUnidades));
    }

    private Economia economiaTransformar(Lote lote) {
        BigDecimal custoTotal = riskScoringService.calcularCustoTotal(lote);
        BigDecimal valorNovoProduto = custoTotal.multiply(TRANSFORM_MARKUP);
        BigDecimal perdaEvitada = custoTotal.multiply(CUSTO_DESCARTE_FATOR); // todos os insumos são reaproveitados
        return new Economia(null, null,
                escala(valorNovoProduto),
                escala(perdaEvitada),
                escala(valorNovoProduto.add(perdaEvitada)),
                lote.getQuantidade());
    }

    private Economia economiaCombinar(Lote lote) {
        Produto produto = lote.getProduto();
        double unidades = lote.getQuantidade() * COMBO_SELLTHROUGH.doubleValue();
        BigDecimal receita = bd(produto.getPrecoUnit().doubleValue() * unidades); // combo a preço cheio
        BigDecimal perdaEvitada = bd(produto.getCustoUnit().doubleValue() * unidades).multiply(CUSTO_DESCARTE_FATOR);
        return new Economia(null, produto.getComboNatural(),
                escala(receita),
                escala(perdaEvitada),
                escala(receita.add(perdaEvitada)),
                (int) Math.round(unidades));
    }

    private Economia economiaReposicionar(Lote lote) {
        Produto produto = lote.getProduto();
        double unidades = lote.getQuantidade() * REPO_SELLTHROUGH.doubleValue();
        BigDecimal receita = bd(produto.getPrecoUnit().doubleValue() * unidades);
        BigDecimal perdaEvitada = bd(produto.getCustoUnit().doubleValue() * unidades).multiply(CUSTO_DESCARTE_FATOR);
        return new Economia(null, null,
                escala(receita),
                escala(perdaEvitada),
                escala(receita.add(perdaEvitada)),
                (int) Math.round(unidades));
    }

    private Economia economiaDoar(Lote lote) {
        BigDecimal custoTotal = riskScoringService.calcularCustoTotal(lote);
        BigDecimal perdaEvitada = custoTotal.multiply(CUSTO_DESCARTE_FATOR); // evita o descarte integral
        BigDecimal beneficioFiscal = custoTotal.multiply(DOAR_FISCAL_PCT);
        return new Economia(null, null,
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), // doação não gera receita de venda
                escala(perdaEvitada),
                escala(perdaEvitada.add(beneficioFiscal)),
                lote.getQuantidade());
    }

    private double baseGiro(Lote lote) {
        return volumeRepository.findByLote(lote)
                .map(Volume::getGiroEstoque)
                .filter(g -> g != null && g.doubleValue() > 0)
                .map(BigDecimal::doubleValue)
                .orElse(BASE_GIRO_DEFAULT);
    }

    // ------------------------------------------------------------------
    // Racional — texto-regra determinístico com os números reais da decisão.
    // ------------------------------------------------------------------
    private String montarRacional(Lote lote, TipoRota rota, Economia eco) {
        Produto p = lote.getProduto();
        NivelRisco nivel = riskScoringService.calcularNivel(lote);
        long dias = riskScoringService.diasParaVencer(lote);
        return switch (rota) {
            case REMARCAR -> "Nível %s: %d un. e %d dia(s) até o vencimento. Desconto ótimo de %d%% maximiza a receita — vende ~%d un., recupera R$ %s e evita R$ %s de perda. Aciona campanha multicanal."
                    .formatted(nivel, lote.getQuantidade(), dias, eco.descontoPct(), eco.unidades(),
                            eco.receitaRecuperada(), eco.perdaEvitada());
            case TRANSFORMAR -> "%s é transformável e está em nível %s (%d dia(s)). Em vez de descartar, vira \"%s\", produto de maior margem — estimativa de R$ %s recuperados. Ficha técnica detalhada gerada pela IA."
                    .formatted(p.getNome(), nivel, dias,
                            p.getTransformDestino() != null ? p.getTransformDestino() : "produto novo",
                            eco.economiaProjetada());
            case COMBINAR -> "%s casa com %s (parado em estoque). Combo move ~%d un. a preço cheio, recuperando R$ %s sem queimar margem com desconto."
                    .formatted(p.getNome(),
                            eco.comboProduto() != null ? eco.comboProduto().getNome() : "complemento",
                            eco.unidades(), eco.receitaRecuperada());
            case REPOSICIONAR -> "%s em nível MÉDIO com giro baixo. Mover para ponta de gôndola/checkout aumenta a exposição — estimativa de ~%d un. escoadas, R$ %s recuperados."
                    .formatted(p.getNome(), eco.unidades(), eco.receitaRecuperada());
            case DOAR -> "%s a %d dia(s) do vencimento e sem giro de venda. Doar evita R$ %s de descarte e gera benefício fiscal — economia total estimada de R$ %s."
                    .formatted(p.getNome(), dias, eco.perdaEvitada(), eco.economiaProjetada());
        };
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    private static BigDecimal escala(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    /** Resultado da economia de uma rota. {@code unidades} é informativo (entra no racional). */
    private record Economia(
            Integer descontoPct,
            Produto comboProduto,
            BigDecimal receitaRecuperada,
            BigDecimal perdaEvitada,
            BigDecimal economiaProjetada,
            Integer unidades) {
    }
}
