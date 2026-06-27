package br.com.resgateai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "impacto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Impacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(precision = 10, scale = 2)
    private BigDecimal kgSalvo;

    @Column(name = "co2_evitado", precision = 10, scale = 2)
    private BigDecimal co2Evitado;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorRs;

    @Builder.Default
    private LocalDateTime registradoEm = LocalDateTime.now();
}
