package br.com.resgateai.repository;

import br.com.resgateai.entity.Lote;
import br.com.resgateai.entity.Produto;
import br.com.resgateai.entity.Volume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VolumeRepository extends JpaRepository<Volume, Long> {

    List<Volume> findByProduto(Produto produto);

    Optional<Volume> findByLote(Lote lote);
}
