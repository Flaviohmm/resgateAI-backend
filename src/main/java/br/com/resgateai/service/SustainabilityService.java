package br.com.resgateai.service;

import br.com.resgateai.dto.response.DashboardResponse;
import br.com.resgateai.dto.response.ImpactoPontoResponse;
import br.com.resgateai.entity.Impacto;
import br.com.resgateai.entity.Recomendacao;
import br.com.resgateai.repository.ImpactoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Agrega o impacto de sustentabilidade (Arquitetura §3, passo [6]) a cada rota
 * efetivamente executada (Remarcar enviado, Transformar gerado). Os fatores de
 * conversão são ilustrativos (PRD §9: rodapé "valores ilustrativos" obrigatório),
 * já que o catálogo não tem peso por unidade — kg salvo usa um peso médio de
 * embalagem e CO2 evitado usa o fator ~2,5 kg CO2e por kg de alimento (FAO/WRAP).
 */
@Service
@RequiredArgsConstructor
public class SustainabilityService {

    private static final BigDecimal KG_POR_UNIDADE = new BigDecimal("0.35");
    private static final BigDecimal FATOR_CO2_POR_KG = new BigDecimal("2.5");

    private final ImpactoRepository impactoRepository;

    @Transactional
    public void registrarImpacto(Recomendacao recomendacao) {
        BigDecimal quantidade = BigDecimal.valueOf(recomendacao.getLote().getQuantidade());
        BigDecimal kgSalvo = quantidade.multiply(KG_POR_UNIDADE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal co2Evitado = kgSalvo.multiply(FATOR_CO2_POR_KG).setScale(2, RoundingMode.HALF_UP);
        BigDecimal valorRs = recomendacao.getReceitaRecuperada() != null
                ? recomendacao.getReceitaRecuperada() : BigDecimal.ZERO;

        impactoRepository.save(Impacto.builder()
                .kgSalvo(kgSalvo)
                .co2Evitado(co2Evitado)
                .valorRs(valorRs)
                .build());
    }

    public DashboardResponse dashboard() {
        List<Impacto> registros = impactoRepository.findAllByOrderByRegistradoEmAsc();

        BigDecimal valorAcumulado = BigDecimal.ZERO;
        BigDecimal kgTotal = BigDecimal.ZERO;
        BigDecimal co2Total = BigDecimal.ZERO;
        List<ImpactoPontoResponse> evolucao = new ArrayList<>();

        int ordem = 0;
        for (Impacto impacto : registros) {
            ordem++;
            valorAcumulado = valorAcumulado.add(nz(impacto.getValorRs()));
            kgTotal = kgTotal.add(nz(impacto.getKgSalvo()));
            co2Total = co2Total.add(nz(impacto.getCo2Evitado()));
            evolucao.add(ImpactoPontoResponse.builder()
                    .ordem(ordem)
                    .valorAcumulado(valorAcumulado.setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        return DashboardResponse.builder()
                .valorRecuperado(valorAcumulado.setScale(2, RoundingMode.HALF_UP))
                .kgSalvo(kgTotal.setScale(2, RoundingMode.HALF_UP))
                .co2Evitado(co2Total.setScale(2, RoundingMode.HALF_UP))
                .produtosResgatados(registros.size())
                .evolucao(evolucao)
                .build();
    }

    private BigDecimal nz(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
