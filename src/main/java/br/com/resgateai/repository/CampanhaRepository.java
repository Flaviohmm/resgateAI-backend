package br.com.resgateai.repository;

import br.com.resgateai.entity.Campanha;
import br.com.resgateai.entity.Recomendacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CampanhaRepository extends JpaRepository<Campanha, Long> {

    Optional<Campanha> findByRecomendacao(Recomendacao recomendacao);
}
