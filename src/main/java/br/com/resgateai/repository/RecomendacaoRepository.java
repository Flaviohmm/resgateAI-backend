package br.com.resgateai.repository;

import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Recomendacao;
import br.com.resgateai.enums.TipoRota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecomendacaoRepository extends JpaRepository<Recomendacao, Long> {

    Optional<Recomendacao> findByLote(Lote lote);

    List<Recomendacao> findByRota(TipoRota rota);
}
