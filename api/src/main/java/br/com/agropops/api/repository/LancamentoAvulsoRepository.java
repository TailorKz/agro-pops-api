package br.com.agropops.api.repository;

import br.com.agropops.api.model.LancamentoAvulso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LancamentoAvulsoRepository extends JpaRepository<LancamentoAvulso, Long> {

    @Query("SELECT l FROM LancamentoAvulso l WHERE l.produtor.id = :produtorId AND EXTRACT(YEAR FROM l.data) = :ano")
    List<LancamentoAvulso> findByProdutorIdAndAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    boolean existsByPropriedadeRuralId(Long propriedadeRuralId);
    // =========================================================================
    // UMA ÚNICA QUERY PARA TODOS OS CÁLCULOS AVULSOS
    // Retorna: [0] = Receitas, [1] = Saídas Totais, [2] = Saídas Dedutíveis
    // =========================================================================
    @Query("SELECT " +
            "COALESCE(SUM(CASE WHEN l.tipo = 'ENTRADA' THEN l.valor ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN l.tipo = 'SAIDA' THEN l.valor ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN l.tipo = 'SAIDA' AND l.isDedutivel = true THEN l.valor ELSE 0 END), 0) " +
            "FROM LancamentoAvulso l WHERE l.produtor.id = :produtorId AND EXTRACT(YEAR FROM l.data) = :ano")
    List<Object[]> getResumoFinanceiroAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);
}