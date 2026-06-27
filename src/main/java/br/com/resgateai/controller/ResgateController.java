package br.com.resgateai.controller;

import br.com.resgateai.dto.response.CampaignResponse;
import br.com.resgateai.dto.response.DispatchResponse;
import br.com.resgateai.dto.response.ProdutoResponse;
import br.com.resgateai.dto.response.QueueResponse;
import br.com.resgateai.dto.response.RecommendationResponse;
import br.com.resgateai.dto.response.SugestaoPedidoResponse;
import br.com.resgateai.dto.response.TransformacaoResponse;
import br.com.resgateai.entity.Lote;
import br.com.resgateai.enums.NivelRisco;
import br.com.resgateai.repository.LoteRepository;
import br.com.resgateai.repository.ProdutoRepository;
import br.com.resgateai.service.CampaignAIService;
import br.com.resgateai.service.CampaignDispatchService;
import br.com.resgateai.service.LearningLoopService;
import br.com.resgateai.service.RiskScoringService;
import br.com.resgateai.service.RoutingService;
import br.com.resgateai.service.TransformationAIService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/resgate")
@RequiredArgsConstructor
public class ResgateController {

    private final LoteRepository loteRepository;
    private final ProdutoRepository produtoRepository;
    private final RiskScoringService riskScoringService;
    private final LearningLoopService learningLoopService;
    private final RoutingService routingService;
    private final CampaignAIService campaignAIService;
    private final CampaignDispatchService campaignDispatchService;
    private final TransformationAIService transformationAIService;

    @GetMapping("/fila")
    public List<QueueResponse> fila() {
        return loteRepository.findAll().stream()
                .map(this::toQueueResponse)
                .sorted(Comparator.comparing(QueueResponse::getPerdaPotencial).reversed())
                .toList();
    }

    @GetMapping("/recomendacao/{loteId}")
    public RecommendationResponse recomendacao(@PathVariable Long loteId) {
        return routingService.recomendar(loteId);
    }

    @PostMapping("/campanha/{recomendacaoId}")
    public CampaignResponse gerarCampanha(@PathVariable Long recomendacaoId) {
        return campaignAIService.gerarCampanha(recomendacaoId);
    }

    @PostMapping("/campanha/{campanhaId}/enviar")
    public DispatchResponse enviarCampanha(@PathVariable Long campanhaId) {
        return campaignDispatchService.enviar(campanhaId);
    }

    @PostMapping("/transformar/{recomendacaoId}")
    public TransformacaoResponse transformar(@PathVariable Long recomendacaoId) {
        return transformationAIService.transformar(recomendacaoId);
    }

    @GetMapping("/sugestoes-pedido")
    public List<SugestaoPedidoResponse> sugestoesPedido() {
        return learningLoopService.gerarSugestoesPedido();
    }

    @GetMapping("/produtos")
    public List<ProdutoResponse> produtos() {
        return produtoRepository.findAll().stream()
                .map(p -> ProdutoResponse.builder().produtoId(p.getId()).nome(p.getNome()).build())
                .sorted(Comparator.comparing(ProdutoResponse::getNome))
                .toList();
    }

    private QueueResponse toQueueResponse(Lote lote) {
        NivelRisco nivel = riskScoringService.calcularNivel(lote);
        learningLoopService.registrarEntradaNaFila(lote, nivel);
        return QueueResponse.builder()
                .loteId(lote.getId())
                .produto(lote.getProduto().getNome())
                .categoria(lote.getProduto().getCategoria())
                .quantidade(lote.getQuantidade())
                .diasParaVencer((int) riskScoringService.diasParaVencer(lote))
                .nivel(nivel)
                .perdaPotencial(riskScoringService.calcularPerdaPotencial(lote))
                .build();
    }
}
