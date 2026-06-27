package br.com.resgateai.repository;

import br.com.resgateai.entity.ProdutoTransformado;
import br.com.resgateai.entity.Recomendacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProdutoTransformadoRepository extends JpaRepository<ProdutoTransformado, Long> {

    Optional<ProdutoTransformado> findByRecomendacao(Recomendacao recomendacao);
}
