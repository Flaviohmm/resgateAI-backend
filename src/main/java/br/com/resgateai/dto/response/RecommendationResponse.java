package br.com.resgateai.dto.response;

import br.com.resgateai.enums.TipoRota;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationResponse {

    private Long recomendacaoId;

    private TipoRota rota;

    private Integer descontoPct;

    private String produtoComb;

    private BigDecimal receitaRecuperada;

    private BigDecimal perdaEvitada;

    private BigDecimal economiaProjetada;

    private String racional;
}
