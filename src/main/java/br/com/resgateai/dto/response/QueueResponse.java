package br.com.resgateai.dto.response;

import br.com.resgateai.enums.CategoriaProduto;
import br.com.resgateai.enums.NivelRisco;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueResponse {

    private Long loteId;

    private String produto;

    private CategoriaProduto categoria;

    private Integer quantidade;

    private Integer diasParaVencer;

    private NivelRisco nivel;

    private BigDecimal perdaPotencial;
}
