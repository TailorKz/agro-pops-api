package br.com.agropops.api.repository;

import br.com.agropops.api.model.NotaFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, Long> {

    // Traz a nota, os itens e as parcelas em apenas 1 viagem
    @Query("SELECT DISTINCT n FROM NotaFiscal n LEFT JOIN FETCH n.itens LEFT JOIN FETCH n.parcelas WHERE n.produtor.id = :produtorId ORDER BY n.dataEmissao DESC")
    List<NotaFiscal> findByProdutorIdOrderByDataEmissaoDesc(@Param("produtorId") Long produtorId);

    List<NotaFiscal> findByProdutorId(Long produtorId);

    // ATUALIZADO: Filtra e Ordena pela Data de Vencimento da Parcela (Regime de Caixa)
    @Query("SELECT DISTINCT n FROM NotaFiscal n LEFT JOIN FETCH n.itens LEFT JOIN FETCH n.parcelas p " +
            "WHERE n.produtor.id = :produtorId AND p.dataVencimento BETWEEN :dataInicio AND :dataFim " +
            "ORDER BY p.dataVencimento DESC")
    List<NotaFiscal> findByProdutorIdAndDataEmissaoBetweenOrderByDataEmissaoDesc(
            @Param("produtorId") Long produtorId,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim);

    boolean existsByChaveAcesso(String chaveAcesso);

    @Query("SELECT n.chaveAcesso FROM NotaFiscal n WHERE n.produtor.id = :produtorId")
    java.util.Set<String> findChavesAcessoByProdutorId(@Param("produtorId") Long produtorId);

    // ATUALIZADO: Filtra pelo ano da parcela
    @Query("SELECT DISTINCT n FROM NotaFiscal n JOIN FETCH n.itens JOIN FETCH n.parcelas p " +
            "WHERE n.produtor.id = :produtorId AND EXTRACT(YEAR FROM p.dataVencimento) = :ano")
    List<NotaFiscal> findByProdutorIdAndAnoWithItens(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    // =========================================================================
    // CÁLCULOS DO LIVRO CAIXA E VISÃO GERAL (MOTOR FINANCEIRO EM REGIME DE CAIXA)
    // =========================================================================

    // Soma Entradas (Agora soma o VALOR DA PARCELA no ano de vencimento)
    @Query("SELECT COALESCE(SUM(p.valor), 0) FROM ParcelaNota p JOIN p.notaFiscal n " +
            "WHERE n.produtor.id = :produtorId AND EXTRACT(YEAR FROM p.dataVencimento) = :ano AND n.tipo = 'ENTRADA'")
    BigDecimal sumReceitasByProdutorAndAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    // Soma Saídas Totais (Agora soma o VALOR DA PARCELA no ano de vencimento)
    @Query("SELECT COALESCE(SUM(p.valor), 0) FROM ParcelaNota p JOIN p.notaFiscal n " +
            "WHERE n.produtor.id = :produtorId AND EXTRACT(YEAR FROM p.dataVencimento) = :ano AND n.tipo = 'SAIDA'")
    BigDecimal sumTotalSaidasNfeByProdutorAndAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    // Soma Apenas os Itens Dedutíveis das Notas de Saída que POSSUEM pagamento naquele ano.
    // A trava de segurança matemática no LivroCaixaService.calcularTotais() garante que o
    // total dedutível nunca ultrapasse o valor efetivamente pago (sumTotalSaidasNfe) no ano.
    @Query("SELECT COALESCE(SUM(i.valor), 0) FROM ItemNota i JOIN i.notaFiscal n " +
            "WHERE n.produtor.id = :produtorId AND n.tipo = 'SAIDA' AND i.isDedutivel = true " +
            "AND EXISTS (SELECT 1 FROM ParcelaNota p WHERE p.notaFiscal = n AND EXTRACT(YEAR FROM p.dataVencimento) = :ano)")
    BigDecimal sumDespesasDedutiveisNfeByProdutorAndAno(@Param("produtorId") Long produtorId, @Param("ano") int ano);

    // =========================================================================

    // ATUALIZADO: Usado para buscar as notas num período de datas com base no Pagamento
    @Query("SELECT DISTINCT n FROM NotaFiscal n LEFT JOIN FETCH n.parcelas p " +
            "WHERE n.produtor.id = :produtorId AND p.dataVencimento >= :inicio AND p.dataVencimento <= :fim " +
            "ORDER BY p.dataVencimento DESC")
    List<NotaFiscal> findByProdutorAndDataEmissaoBetween(
            @Param("produtorId") Long produtorId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim);

    // Traz a nota com seus itens e parcelas filtrando pelo ANO DO VENCIMENTO
    @Query("SELECT DISTINCT n FROM NotaFiscal n " +
            "LEFT JOIN FETCH n.itens " +
            "LEFT JOIN FETCH n.parcelas p " +
            "WHERE n.produtor.id = :produtorId AND EXTRACT(YEAR FROM p.dataVencimento) = :ano")
    List<NotaFiscal> findByProdutorIdAndAnoVencimento(
            @Param("produtorId") Long produtorId,
            @Param("ano") int ano);
}