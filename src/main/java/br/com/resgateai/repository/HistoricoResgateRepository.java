package br.com.resgateai.repository;

import br.com.resgateai.entity.HistoricoResgate;
import br.com.resgateai.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface HistoricoResgateRepository extends JpaRepository<HistoricoResgate, Long> {

    List<HistoricoResgate> findByProdutoAndSemanaRefAfter(Produto produto, LocalDate semanaRef);

    List<HistoricoResgate> findByProduto(Produto produto);

    boolean existsByProdutoAndSemanaRef(Produto produto, LocalDate semanaRef);

    List<HistoricoResgate> findBySemanaRefGreaterThanEqual(LocalDate semanaRef);
}
