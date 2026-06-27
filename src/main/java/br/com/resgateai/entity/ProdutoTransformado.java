package br.com.resgateai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "produto_transformado")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoTransformado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recomendacao_id", nullable = false)
    private Recomendacao recomendacao;

    @Column(length = 120)
    private String produtoNovo;

    private String ingredientes;

    private String modoPreparo;

    @Column(precision = 10, scale = 2)
    private BigDecimal precoSugerido;

    private String argumentoVenda;

    @Builder.Default
    private LocalDateTime geradoEm = LocalDateTime.now();
}
