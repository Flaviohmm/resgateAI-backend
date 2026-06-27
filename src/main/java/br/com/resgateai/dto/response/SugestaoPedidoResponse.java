package br.com.resgateai.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SugestaoPedidoResponse {

    private Long produtoId;

    private String nome;

    private Integer ocorrenciasRisco;

    private Integer ajustePct;

    private String racional;
}
