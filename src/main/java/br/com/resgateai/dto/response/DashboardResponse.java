package br.com.resgateai.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    private BigDecimal valorRecuperado;

    private BigDecimal co2Evitado;

    private BigDecimal kgSalvo;

    private Integer produtosResgatados;

    private List<ImpactoPontoResponse> evolucao;
}
