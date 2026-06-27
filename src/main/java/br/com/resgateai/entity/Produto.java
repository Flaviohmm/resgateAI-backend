package br.com.resgateai.entity;

import br.com.resgateai.enums.CategoriaProduto;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "produto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private CategoriaProduto categoria;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal custoUnit;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnit;

    @Column(nullable = false)
    private Integer validadeDias;

    @Builder.Default
    @Column(nullable = false)
    private Boolean transformavel = false;

    @Column(length = 120)
    private String transformDestino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "combo_natural_id")
    private Produto comboNatural;

    @OneToMany(mappedBy = "produto")
    @Builder.Default
    private List<Lote> lotes = new ArrayList<>();
}
