package br.com.agropops.api.repository;

import br.com.agropops.api.model.NotaFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, Long> {

    // 1. Busca Padrão: Faze FETCH apenas nas parcelas. Os itens vêm sob demanda em lote (BatchSize).
    @Query("SELECT DISTINCT n FROM NotaFiscal n LEFT JOIN FETCH n.parcelas " +
            "WHERE n.produtor.id = :produtorId ORDER BY n.dataEmissao DESC")
    List<NotaFiscal> findByProdutorIdOrderByDataEmissaoDesc(@Param("produtorId") Long produtorId);

    List<NotaFiscal> findByProdutorId(Long produtorId);

    // 2. Busca por Data
    @Query("SELECT DISTINCT n FROM NotaFiscal n LEFT JOIN FETCH n.parcelas " +
            "WHERE n.produtor.id = :produtorId AND n.dataEmissao BETWEEN :dataInicio AND :dataFim " +
            "ORDER BY n.dataEmissao DESC")
    List<NotaFiscal> findByProdutorIdAndDataEmissaoBetweenOrderByDataEmissaoDesc(
            @Param("produtorId") Long produtorId,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim);

    boolean existsByChaveAcesso(String chaveAcesso);
    java.util.Optional<NotaFiscal> findByChaveAcesso(String chaveAcesso);

    @Query("SELECT n.chaveAcesso FROM NotaFiscal n WHERE n.produtor.id = :produtorId")
    java.util.Set<String> findChavesAcessoByProdutorId(@Param("produtorId") Long produtorId);

    @Query("SELECT DISTINCT n FROM NotaFiscal n LEFT JOIN FETCH n.parcelas p " +
            "WHERE n.produtor.id = :produtorId AND EXTRACT(YEAR FROM p.dataVencimento) = :ano")
    List<NotaFiscal> findByProdutorIdAndAnoWithItens(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    @Query("SELECT DISTINCT n FROM NotaFiscal n LEFT JOIN FETCH n.parcelas p " +
            "WHERE n.produtor.id = :produtorId AND p.dataVencimento >= :inicio AND p.dataVencimento <= :fim " +
            "ORDER BY p.dataVencimento DESC")
    List<NotaFiscal> findByProdutorAndDataEmissaoBetween(
            @Param("produtorId") Long produtorId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    @Query("SELECT DISTINCT n FROM NotaFiscal n LEFT JOIN FETCH n.parcelas " +
            "WHERE n.produtor.id = :produtorId " +
            "AND EXISTS (SELECT 1 FROM ParcelaNota p2 WHERE p2.notaFiscal = n AND EXTRACT(YEAR FROM p2.dataVencimento) = :ano)")
    List<NotaFiscal> findByProdutorIdAndAnoVencimento(
            @Param("produtorId") Long produtorId,
            @Param("ano") int ano);

    // =========================================================================
    // MOTOR FINANCEIRO DE ALTA PERFORMANCE (REGIME DE CAIXA CONSOLIDADO)
    // =========================================================================

    @Query("SELECT " +
            "COALESCE(SUM(CASE WHEN n.tipo = 'ENTRADA' THEN p.valor ELSE 0 END), 0), " +
            "COALESCE(SUM(CASE WHEN n.tipo = 'SAIDA' THEN p.valor ELSE 0 END), 0) " +
            "FROM ParcelaNota p JOIN p.notaFiscal n " +
            "WHERE n.produtor.id = :produtorId AND EXTRACT(YEAR FROM p.dataVencimento) = :ano")
    List<Object[]> getResumoParcelasAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    @Query("SELECT COALESCE(SUM(i.valor), 0) FROM ItemNota i JOIN i.notaFiscal n " +
            "WHERE n.produtor.id = :produtorId AND n.tipo = 'SAIDA' AND i.isDedutivel = true " +
            "AND EXISTS (SELECT 1 FROM ParcelaNota p WHERE p.notaFiscal = n AND EXTRACT(YEAR FROM p.dataVencimento) = :ano)")
    BigDecimal getResumoItensDedutiveisAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    // EXCLUSÃO EM MASSA

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM ItemNota i WHERE i.notaFiscal.id IN :notaIds")
    void deleteAllItensByNotaIds(@Param("notaIds") List<Long> notaIds);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM ParcelaNota p WHERE p.notaFiscal.id IN :notaIds")
    void deleteAllParcelasByNotaIds(@Param("notaIds") List<Long> notaIds);

    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM NotaFiscal n WHERE n.id IN :notaIds")
    void deleteAllNotasByIds(@Param("notaIds") List<Long> notaIds);
}