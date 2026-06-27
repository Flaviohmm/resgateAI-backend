package br.com.resgateai.entity;

import br.com.resgateai.enums.TipoRota;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "recomendacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recomendacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id", nullable = false)
    private Lote lote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoRota rota;

    private Integer descontoPct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_produto_id")
    private Produto comboProduto;

    @Column(precision = 10, scale = 2)
    private BigDecimal receitaRecuperada;

    @Column(precision = 10, scale = 2)
    private BigDecimal perdaEvitada;

    private String racional;

    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();
}
