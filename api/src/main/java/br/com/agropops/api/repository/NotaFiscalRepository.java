package br.com.agropops.api.repository;

import br.com.agropops.api.model.NotaFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, Long> {

    // traz a nota e os itens em apenas 1 viagem
    @Query("SELECT DISTINCT n FROM NotaFiscal n LEFT JOIN FETCH n.itens WHERE n.produtor.id = :produtorId ORDER BY n.dataEmissao DESC")
    List<NotaFiscal> findByProdutorIdOrderByDataEmissaoDesc(@Param("produtorId") Long produtorId);

    List<NotaFiscal> findByProdutorId(Long produtorId);

    List<NotaFiscal> findByProdutorIdAndDataEmissaoBetweenOrderByDataEmissaoDesc(Long produtorId, LocalDate dataInicio, LocalDate dataFim);

    boolean existsByChaveAcesso(String chaveAcesso);

    @Query("SELECT n.chaveAcesso FROM NotaFiscal n WHERE n.produtor.id = :produtorId")
    java.util.Set<String> findChavesAcessoByProdutorId(@Param("produtorId") Long produtorId);

    // carrega as notas e os itens numa única viagem
    @Query("SELECT DISTINCT n FROM NotaFiscal n JOIN FETCH n.itens WHERE n.produtor.id = :produtorId AND EXTRACT(YEAR FROM n.dataEmissao) = :ano")
    List<NotaFiscal> findByProdutorIdAndAnoWithItens(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    // Soma Entradas (Receita Bruta da NF-e)
    @Query("SELECT COALESCE(SUM(n.valorTotal), 0) FROM NotaFiscal n WHERE n.produtor.id = :produtorId AND EXTRACT(YEAR FROM n.dataEmissao) = :ano AND n.tipo = 'ENTRADA'")
    BigDecimal sumReceitasByProdutorAndAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    // Soma Saídas Totais (NF-e) - Usado para a trava matemática
    @Query("SELECT COALESCE(SUM(n.valorTotal), 0) FROM NotaFiscal n WHERE n.produtor.id = :produtorId AND EXTRACT(YEAR FROM n.dataEmissao) = :ano AND n.tipo = 'SAIDA'")
    BigDecimal sumTotalSaidasNfeByProdutorAndAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    // Soma Apenas os Itens Dedutíveis das Notas de Saída
    @Query("SELECT COALESCE(SUM(i.valor), 0) FROM ItemNota i JOIN i.notaFiscal n WHERE n.produtor.id = :produtorId AND EXTRACT(YEAR FROM n.dataEmissao) = :ano AND n.tipo = 'SAIDA' AND i.isDedutivel = true")
    BigDecimal sumDespesasDedutiveisNfeByProdutorAndAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);
}