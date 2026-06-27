package br.com.resgateai.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DispatchResponse {

    private String status;

    private LocalDateTime enviadaEm;
}
