package br.com.resgateai.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignResponse {

    private Long campanhaId;

    private String whatsapp;

    private String story;

    private String etiqueta;

    private Boolean enviada;
}
