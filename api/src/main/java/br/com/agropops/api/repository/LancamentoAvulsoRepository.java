package br.com.agropops.api.repository;

import br.com.agropops.api.model.LancamentoAvulso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LancamentoAvulsoRepository extends JpaRepository<LancamentoAvulso, Long> {

    @Query("SELECT l FROM LancamentoAvulso l WHERE l.produtor.id = :produtorId AND EXTRACT(YEAR FROM l.data) = :ano")
    List<LancamentoAvulso> findByProdutorIdAndAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    // Soma Entradas (Receita Avulsa)
    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM LancamentoAvulso l WHERE l.produtor.id = :produtorId AND EXTRACT(YEAR FROM l.data) = :ano AND l.tipo = 'ENTRADA'")
    BigDecimal sumReceitasByProdutorAndAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    // Soma Saídas Totais Avulsas - Usado para a trava matemática
    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM LancamentoAvulso l WHERE l.produtor.id = :produtorId AND EXTRACT(YEAR FROM l.data) = :ano AND l.tipo = 'SAIDA'")
    BigDecimal sumTotalSaidasAvulsoByProdutorAndAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    // Soma Apenas Saídas Avulsas Dedutíveis
    @Query("SELECT COALESCE(SUM(l.valor), 0) FROM LancamentoAvulso l WHERE l.produtor.id = :produtorId AND EXTRACT(YEAR FROM l.data) = :ano AND l.tipo = 'SAIDA' AND l.isDedutivel = true")
    BigDecimal sumDespesasDedutiveisAvulsoByProdutorAndAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);
}