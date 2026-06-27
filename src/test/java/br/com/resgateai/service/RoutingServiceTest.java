package br.com.resgateai.service;

import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Produto;
import br.com.resgateai.entity.Volume;
import br.com.resgateai.enums.CategoriaProduto;
import br.com.resgateai.enums.TipoRota;
import br.com.resgateai.repository.LoteRepository;
import br.com.resgateai.repository.RecomendacaoRepository;
import br.com.resgateai.repository.VolumeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Valida a árvore de decisão (Arquitetura §3.2) sem precisar de banco: monta lotes
 * em memória espelhando o seed e confere a rota escolhida. O critério de aceite da
 * Story 2.3 é que as 5 rotas apareçam ao menos uma vez — cada teste cobre uma rota.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoutingServiceTest {

    @Mock
    private LoteRepository loteRepository;
    @Mock
    private RecomendacaoRepository recomendacaoRepository;
    @Mock
    private VolumeRepository volumeRepository;

    private final RiskScoringService riskScoringService = new RiskScoringService();

    private RoutingService routingService() {
        return new RoutingService(loteRepository, recomendacaoRepository, volumeRepository, riskScoringService);
    }

    private Produto produto(String nome, int validadeDias, boolean transformavel) {
        return Produto.builder()
                .id(1L)
                .nome(nome)
                .categoria(CategoriaProduto.MERCEARIA)
                .custoUnit(new BigDecimal("3.00"))
                .precoUnit(new BigDecimal("4.99"))
                .validadeDias(validadeDias)
                .transformavel(transformavel)
                .build();
    }

    private Lote lote(Produto produto, int quantidade, int diasParaVencer) {
        return Lote.builder()
                .id(99L)
                .produto(produto)
                .quantidade(quantidade)
                .dataRecebido(LocalDate.now().minusDays(5))
                .dataValidade(LocalDate.now().plusDays(diasParaVencer))
                .build();
    }

    @Test
    void transformavelEmRiscoCriticoVaiParaTransformar() {
        // Banana Prata: transformável, 30 un., vence em 2 dias -> CRITICO
        Produto banana = produto("Banana Prata kg", 7, true);
        assertThat(routingService().decidirRota(lote(banana, 30, 2)))
                .isEqualTo(TipoRota.TRANSFORMAR);
    }

    @Test
    void criticoComQuantidadeAltaVaiParaRemarcar() {
        // Leite Integral: não transformável, 25 un. (>= 20), vence em 1 dia -> CRITICO
        Produto leite = produto("Leite Integral 1L", 10, false);
        assertThat(routingService().decidirRota(lote(leite, 25, 1)))
                .isEqualTo(TipoRota.REMARCAR);
    }

    @Test
    void produtoComComplementoEmEstoqueVaiParaCombinar() {
        // Queijo Mussarela: tem combo natural (Pão de Forma), que está em estoque
        Produto pao = produto("Pão de Forma 500g", 8, true);
        Produto queijo = produto("Queijo Mussarela 400g", 30, false);
        queijo.setComboNatural(pao);
        when(loteRepository.findByProduto(pao)).thenReturn(List.of(lote(pao, 22, 3)));

        assertThat(routingService().decidirRota(lote(queijo, 8, 20)))
                .isEqualTo(TipoRota.COMBINAR);
    }

    @Test
    void nivelMedioComGiroBaixoVaiParaReposicionar() {
        // Macarrão: validade 540, vence em 120 dias -> MEDIO (120 <= 162); giro 0.80 < 1.0
        Produto macarrao = produto("Macarrão Espaguete 500g", 540, false);
        Lote l = lote(macarrao, 60, 120);
        when(volumeRepository.findByLote(l)).thenReturn(Optional.of(
                Volume.builder().giroEstoque(new BigDecimal("0.80")).build()));

        assertThat(routingService().decidirRota(l)).isEqualTo(TipoRota.REPOSICIONAR);
    }

    @Test
    void aUmDiaDoVencimentoSemGiroVaiParaDoar() {
        // Alface: não transformável, 18 un. (< 20), vence em 1 dia, sem registro de volume
        Produto alface = produto("Alface Crespa unid", 4, false);
        Lote l = lote(alface, 18, 1);
        when(volumeRepository.findByLote(l)).thenReturn(Optional.empty());

        assertThat(routingService().decidirRota(l)).isEqualTo(TipoRota.DOAR);
    }

    @Test
    void semNenhumaRegraCaiNoPadraoRemarcar() {
        // Arroz: validade 365, vence em 300 dias -> BAIXO, nenhuma regra casa
        Produto arroz = produto("Arroz Tipo 1 5kg", 365, false);
        lenient().when(volumeRepository.findByLote(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        assertThat(routingService().decidirRota(lote(arroz, 50, 300)))
                .isEqualTo(TipoRota.REMARCAR);
    }

    @Test
    void asCincoRotasSaoAlcancaveis() {
        Produto pao = produto("Pão de Forma 500g", 8, true);
        Produto queijo = produto("Queijo Mussarela 400g", 30, false);
        queijo.setComboNatural(pao);
        when(loteRepository.findByProduto(pao)).thenReturn(List.of(lote(pao, 22, 3)));

        Produto macarrao = produto("Macarrão Espaguete 500g", 540, false);
        Lote loteMacarrao = lote(macarrao, 60, 120);
        when(volumeRepository.findByLote(loteMacarrao)).thenReturn(Optional.of(
                Volume.builder().giroEstoque(new BigDecimal("0.80")).build()));

        RoutingService rs = routingService();
        List<TipoRota> rotas = List.of(
                rs.decidirRota(lote(produto("Banana Prata kg", 7, true), 30, 2)),
                rs.decidirRota(lote(produto("Leite Integral 1L", 10, false), 25, 1)),
                rs.decidirRota(lote(queijo, 8, 20)),
                rs.decidirRota(loteMacarrao),
                rs.decidirRota(lote(produto("Alface Crespa unid", 4, false), 18, 1)));

        assertThat(rotas).containsExactlyInAnyOrder(
                TipoRota.TRANSFORMAR, TipoRota.REMARCAR, TipoRota.COMBINAR,
                TipoRota.REPOSICIONAR, TipoRota.DOAR);
    }
}
