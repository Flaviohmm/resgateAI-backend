package br.com.resgateai.repository;

import br.com.resgateai.entity.Produto;
import br.com.resgateai.enums.CategoriaProduto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    List<Produto> findByCategoria(CategoriaProduto categoria);

    List<Produto> findByTransformavelTrue();

    Optional<Produto> findByNomeIgnoreCase(String nome);

    List<Produto> findByCategoriaAndTransformavelTrue(CategoriaProduto categoria);
}
