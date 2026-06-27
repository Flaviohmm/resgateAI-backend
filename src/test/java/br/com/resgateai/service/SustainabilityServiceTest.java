package br.com.resgateai.service;

import br.com.resgateai.dto.response.DashboardResponse;
import br.com.resgateai.entity.Impacto;
import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Recomendacao;
import br.com.resgateai.repository.ImpactoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SustainabilityServiceTest {

    @Mock
    private ImpactoRepository impactoRepository;

    private SustainabilityService service() {
        return new SustainabilityService(impactoRepository);
    }

    @Test
    void registraImpactoComKgECo2EstimadosAPartirDaQuantidade() {
        Lote lote = Lote.builder().id(1L).quantidade(100).build();
        Recomendacao rec = Recomendacao.builder().id(10L).lote(lote)
                .receitaRecuperada(new BigDecimal("50.00")).build();

        service().registrarImpacto(rec);

        ArgumentCaptor<Impacto> captor = ArgumentCaptor.forClass(Impacto.class);
        verify(impactoRepository).save(captor.capture());
        Impacto salvo = captor.getValue();

        assertThat(salvo.getKgSalvo()).isEqualByComparingTo("35.00");
        assertThat(salvo.getCo2Evitado()).isEqualByComparingTo("87.50");
        assertThat(salvo.getValorRs()).isEqualByComparingTo("50.00");
    }

    @Test
    void dashboardAgregaTotaisEEvolucaoAcumulada() {
        Impacto i1 = Impacto.builder().kgSalvo(new BigDecimal("10.00"))
                .co2Evitado(new BigDecimal("25.00")).valorRs(new BigDecimal("30.00")).build();
        Impacto i2 = Impacto.builder().kgSalvo(new BigDecimal("5.00"))
                .co2Evitado(new BigDecimal("12.50")).valorRs(new BigDecimal("20.00")).build();
        when(impactoRepository.findAllByOrderByRegistradoEmAsc()).thenReturn(List.of(i1, i2));

        DashboardResponse resp = service().dashboard();

        assertThat(resp.getKgSalvo()).isEqualByComparingTo("15.00");
        assertThat(resp.getCo2Evitado()).isEqualByComparingTo("37.50");
        assertThat(resp.getValorRecuperado()).isEqualByComparingTo("50.00");
        assertThat(resp.getProdutosResgatados()).isEqualTo(2);
        assertThat(resp.getEvolucao()).hasSize(2);
        assertThat(resp.getEvolucao().get(0).getValorAcumulado()).isEqualByComparingTo("30.00");
        assertThat(resp.getEvolucao().get(1).getValorAcumulado()).isEqualByComparingTo("50.00");
    }

    @Test
    void dashboardSemRegistrosDevolveZerados() {
        when(impactoRepository.findAllByOrderByRegistradoEmAsc()).thenReturn(List.of());

        DashboardResponse resp = service().dashboard();

        assertThat(resp.getKgSalvo()).isEqualByComparingTo("0");
        assertThat(resp.getCo2Evitado()).isEqualByComparingTo("0");
        assertThat(resp.getValorRecuperado()).isEqualByComparingTo("0");
        assertThat(resp.getProdutosResgatados()).isZero();
        assertThat(resp.getEvolucao()).isEmpty();
    }
}
