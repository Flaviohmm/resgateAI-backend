package br.com.resgateai.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeResponse {

    private String produto;

    private Integer quantidadeAtual;

    private Integer capacidadePrateleira;

    private Integer diasNaPrateleira;

    private BigDecimal percentualOcupacao;

    private BigDecimal giroEstoque;
}
