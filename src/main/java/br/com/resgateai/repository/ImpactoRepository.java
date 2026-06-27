package br.com.resgateai.repository;

import br.com.resgateai.entity.Impacto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImpactoRepository extends JpaRepository<Impacto, Long> {

    List<Impacto> findAllByOrderByRegistradoEmAsc();
}
