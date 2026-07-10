package br.com.agropops.api.repository;

import br.com.agropops.api.model.LancamentoAvulso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LancamentoAvulsoRepository extends JpaRepository<LancamentoAvulso, Long> {

    @Query("SELECT l FROM LancamentoAvulso l WHERE l.produtor.id = :produtorId AND EXTRACT(YEAR FROM l.data) = :ano")
    List<LancamentoAvulso> findByProdutorIdAndAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);
}