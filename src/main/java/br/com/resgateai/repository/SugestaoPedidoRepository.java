package br.com.resgateai.repository;

import br.com.resgateai.entity.Produto;
import br.com.resgateai.entity.SugestaoPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SugestaoPedidoRepository extends JpaRepository<SugestaoPedido, Long> {

    Optional<SugestaoPedido> findTopByProdutoOrderByGeradoEmDesc(Produto produto);

    List<SugestaoPedido> findAllByOrderByGeradoEmDesc();
}
