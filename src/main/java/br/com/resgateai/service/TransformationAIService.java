package br.com.resgateai.service;

import br.com.resgateai.dto.response.TransformacaoResponse;
import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Produto;
import br.com.resgateai.entity.ProdutoTransformado;
import br.com.resgateai.entity.Recomendacao;
import br.com.resgateai.enums.TipoRota;
import br.com.resgateai.repository.ProdutoTransformadoRepository;
import br.com.resgateai.repository.RecomendacaoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Geração da ficha do novo produto para a rota TRANSFORMAR (Arquitetura §3.4 / §4.2).
 * A IA gera a ficha (nome, ingredientes, modo de preparo, preço, argumento); a decisão
 * de transformar já vem pronta da Recomendação determinística (Story 2.3). Mesmo padrão
 * de upsert idempotente e fallback determinístico do {@link CampaignAIService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransformationAIService {

    /** O produto transformado vende com prêmio sobre o preço cheio do insumo original. */
    private static final BigDecimal PRECO_MARKUP = new BigDecimal("1.8");

    private static final String SISTEMA = """
            Você é chef de aproveitamento. Transforme um perecível maduro em um \
            produto novo de margem. Responda SOMENTE em JSON, sem markdown, no formato:
            { "produto_novo": "...", "ingredientes": "...", "modo_preparo": "...", \
            "preco_sugerido": "...", "argumento_venda": "..." }""";

    private final RecomendacaoRepository recomendacaoRepository;
    private final ProdutoTransformadoRepository produtoTransformadoRepository;
    private final RiskScoringService riskScoringService;
    private final AiContentGenerator aiContentGenerator;
    private final ObjectMapper objectMapper;
    private final SustainabilityService sustainabilityService;

    @Transactional
    public TransformacaoResponse transformar(Long recomendacaoId) {
        Recomendacao rec = recomendacaoRepository.findById(recomendacaoId)
                .orElseThrow(() -> new EntityNotFoundException("Recomendação não encontrada: " + recomendacaoId));

        if (rec.getRota() != TipoRota.TRANSFORMAR) {
            throw new IllegalStateException(
                    "Ficha de transformação só se aplica à rota TRANSFORMAR; rota atual: " + rec.getRota());
        }

        Lote lote = rec.getLote();
        Produto produto = lote.getProduto();
        long dias = Math.max(0, riskScoringService.diasParaVencer(lote));

        Ficha ficha = gerarFicha(produto, lote.getQuantidade(), dias);

        Optional<ProdutoTransformado> existente = produtoTransformadoRepository.findByRecomendacao(rec);
        ProdutoTransformado pt = existente.orElseGet(() -> ProdutoTransformado.builder().recomendacao(rec).build());
        pt.setProdutoNovo(ficha.produtoNovo());
        pt.setIngredientes(ficha.ingredientes());
        pt.setModoPreparo(ficha.modoPreparo());
        pt.setPrecoSugerido(ficha.precoSugerido());
        pt.setArgumentoVenda(ficha.argumentoVenda());
        pt = produtoTransformadoRepository.save(pt);

        if (existente.isEmpty()) {
            sustainabilityService.registrarImpacto(rec);
        }

        return TransformacaoResponse.builder()
                .produtoTransformadoId(pt.getId())
                .produtoNovo(ficha.produtoNovo())
                .ingredientes(ficha.ingredientes())
                .modoPreparo(ficha.modoPreparo())
                .precoSugerido(ficha.precoSugerido())
                .argumentoVenda(ficha.argumentoVenda())
                .build();
    }

    private Ficha gerarFicha(Produto produto, int quantidade, long dias) {
        String destino = produto.getTransformDestino() != null
                ? produto.getTransformDestino() : "produto novo de maior margem";
        String usuario = ("Item: %s, %d unidades, vence em %d dias. Destino sugerido: %s.")
                .formatted(produto.getNome(), quantidade, dias, destino);

        try {
            String json = aiContentGenerator.generateJson(SISTEMA, usuario);
            JsonNode node = objectMapper.readTree(json);

            String produtoNovo = texto(node, "produto_novo");
            String ingredientes = texto(node, "ingredientes");
            String modoPreparo = texto(node, "modo_preparo");
            String argumentoVenda = texto(node, "argumento_venda");
            BigDecimal preco = parsePreco(node.get("preco_sugerido"));

            if (produtoNovo.isBlank() && ingredientes.isBlank() && modoPreparo.isBlank()) {
                throw new IllegalStateException("IA retornou ficha vazia");
            }
            if (produtoNovo.isBlank()) {
                produtoNovo = destino;
            }
            if (preco == null) {
                preco = precoFallback(produto);
            }
            return new Ficha(produtoNovo, ingredientes, modoPreparo, preco, argumentoVenda);
        } catch (Exception e) {
            log.warn("Falha ao gerar ficha via IA para '{}', usando fallback determinístico",
                    produto.getNome(), e);
            return fichaFallback(produto, destino, dias);
        }
    }

    private Ficha fichaFallback(Produto produto, String destino, long dias) {
        String ingredientes = "%s (insumo a aproveitar) + itens de despensa (açúcar, farinha ou tempero conforme a receita)."
                .formatted(produto.getNome());
        String modoPreparo = ("Selecione as unidades de %s ainda próprias para consumo, processe e combine com os "
                + "demais ingredientes; finalize e embale como %s para venda no mesmo dia.")
                .formatted(produto.getNome(), destino);
        String argumento = ("Aproveite %s antes do vencimento transformando em %s — zero desperdício, "
                + "margem nova e apelo de sustentabilidade na gôndola.")
                .formatted(produto.getNome(), destino);
        return new Ficha(destino, ingredientes, modoPreparo, precoFallback(produto), argumento);
    }

    private BigDecimal precoFallback(Produto produto) {
        return produto.getPrecoUnit().multiply(PRECO_MARKUP).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * O preço pode vir como número ou como string formatada ("R$ 12,90", "12.90", "12,90").
     * Extrai o valor numérico de forma tolerante ao formato brasileiro/inglês.
     */
    private BigDecimal parsePreco(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue().setScale(2, RoundingMode.HALF_UP);
        }
        String bruto = node.asText().replaceAll("[^0-9.,]", "");
        if (bruto.isBlank()) {
            return null;
        }
        boolean temVirgula = bruto.contains(",");
        boolean temPonto = bruto.contains(".");
        String normalizado;
        if (temVirgula && temPonto) {
            // o último separador é o decimal; o outro é separador de milhar
            normalizado = bruto.lastIndexOf(',') > bruto.lastIndexOf('.')
                    ? bruto.replace(".", "").replace(",", ".")
                    : bruto.replace(",", "");
        } else if (temVirgula) {
            normalizado = bruto.replace(",", ".");
        } else {
            normalizado = bruto;
        }
        try {
            return new BigDecimal(normalizado).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String texto(JsonNode node, String campo) {
        JsonNode valor = node.get(campo);
        return valor == null || valor.isNull() ? "" : valor.asText().trim();
    }

    private record Ficha(
            String produtoNovo,
            String ingredientes,
            String modoPreparo,
            BigDecimal precoSugerido,
            String argumentoVenda) {
    }
}
