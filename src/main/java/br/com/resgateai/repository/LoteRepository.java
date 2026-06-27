package br.com.resgateai.repository;

import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface LoteRepository extends JpaRepository<Lote, Long> {

    List<Lote> findByProduto(Produto produto);

    List<Lote> findByDataValidadeBefore(LocalDate date);

    List<Lote> findByDataValidadeBetween(LocalDate inicio, LocalDate fim);

    List<Lote> findByQuantidadeGreaterThan(Integer quantidade);

    List<Lote> findByLocalGondola(String localGondola);

}
