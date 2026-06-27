package br.com.resgateai.service;

import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Produto;
import br.com.resgateai.enums.NivelRisco;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class RiskScoringService {

    private static final BigDecimal CUSTO_DESCARTE_PCT = new BigDecimal("0.05");
    private static final BigDecimal LIMITE_MEDIO_FATOR = new BigDecimal("0.3");

    public long diasParaVencer(Lote lote) {
        return ChronoUnit.DAYS.between(LocalDate.now(), lote.getDataValidade());
    }

    public NivelRisco calcularNivel(Lote lote) {
        long dias = diasParaVencer(lote);
        if (dias <= 2) {
            return NivelRisco.CRITICO;
        }
        if (dias <= 5) {
            return NivelRisco.ALTO;
        }
        Produto produto = lote.getProduto();
        BigDecimal limiteMedio = BigDecimal.valueOf(produto.getValidadeDias()).multiply(LIMITE_MEDIO_FATOR);
        if (BigDecimal.valueOf(dias).compareTo(limiteMedio) <= 0) {
            return NivelRisco.MEDIO;
        }
        return NivelRisco.BAIXO;
    }

    public BigDecimal calcularCustoTotal(Lote lote) {
        return lote.getProduto().getCustoUnit().multiply(BigDecimal.valueOf(lote.getQuantidade()));
    }

    public BigDecimal calcularPerdaPotencial(Lote lote) {
        BigDecimal custoTotal = calcularCustoTotal(lote);
        BigDecimal custoDescarte = custoTotal.multiply(CUSTO_DESCARTE_PCT);
        return custoTotal.add(custoDescarte).setScale(2, RoundingMode.HALF_UP);
    }
}
