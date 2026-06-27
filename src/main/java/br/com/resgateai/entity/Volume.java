package br.com.resgateai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "volume")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Volume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @Column(nullable = false)
    private Integer quantidadeAtual;

    @Column(nullable = false)
    private Integer capacidadePrateleira;

    @Column(nullable = false)
    private Integer diasNaPrateleira;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentualOcupacao;

    @Column(precision = 10, scale = 2)
    private BigDecimal giroEstoque;

    @Builder.Default
    private LocalDateTime atualizadoEm = LocalDateTime.now();
}
