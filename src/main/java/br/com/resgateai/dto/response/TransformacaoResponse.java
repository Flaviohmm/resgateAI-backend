package br.com.resgateai.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransformacaoResponse {

    private Long produtoTransformadoId;

    private String produtoNovo;

    private String ingredientes;

    private String modoPreparo;

    private BigDecimal precoSugerido;

    private String argumentoVenda;
}
