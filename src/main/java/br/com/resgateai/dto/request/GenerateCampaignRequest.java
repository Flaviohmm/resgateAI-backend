package br.com.resgateai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateCampaignRequest {

    @NotNull
    private Long recomendacaoId;

    @NotBlank
    private String tom;

    private Boolean gerarWhatsapp;

    private Boolean gerarStory;

    private Boolean gerarEtiqueta;
}
