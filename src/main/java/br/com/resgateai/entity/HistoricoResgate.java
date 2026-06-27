package br.com.resgateai.entity;

import br.com.resgateai.enums.TipoRota;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "historico_resgate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricoResgate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    /**
     * Pode ser nulo: lotes de semanas anteriores já foram liquidados e
     * removidos do estoque corrente, mas o sinal de risco precisa persistir.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private Lote lote;

    @Column(nullable = false)
    private Integer quantidadeEmRisco;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TipoRota rotaAplicada;

    @Column(nullable = false)
    private LocalDate semanaRef;

    @Builder.Default
    private LocalDateTime criadoEm = LocalDateTime.now();
}
