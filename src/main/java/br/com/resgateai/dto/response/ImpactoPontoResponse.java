package br.com.resgateai.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpactoPontoResponse {

    private Integer ordem;

    private BigDecimal valorAcumulado;
}
