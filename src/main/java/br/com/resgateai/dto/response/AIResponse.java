package br.com.resgateai.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIResponse {

    private Boolean sucesso;

    private String tipo;

    private Object conteudo;
}
