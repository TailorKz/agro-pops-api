package br.com.agropops.api.repository;

import br.com.agropops.api.model.NotaFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, Long> {

    // 'JOIN FETCH' traz a nota e os itens em apenas 1 viagem
    @Query("SELECT DISTINCT n FROM NotaFiscal n LEFT JOIN FETCH n.itens WHERE n.produtor.id = :produtorId ORDER BY n.dataEmissao DESC")
    List<NotaFiscal> findByProdutorIdOrderByDataEmissaoDesc(@Param("produtorId") Long produtorId);

    List<NotaFiscal> findByProdutorId(Long produtorId);

    List<NotaFiscal> findByProdutorIdAndDataEmissaoBetweenOrderByDataEmissaoDesc(Long produtorId, LocalDate dataInicio, LocalDate dataFim);

    boolean existsByChaveAcesso(String chaveAcesso);

    @Query("SELECT n.chaveAcesso FROM NotaFiscal n WHERE n.produtor.id = :produtorId")
    java.util.Set<String> findChavesAcessoByProdutorId(@Param("produtorId") Long produtorId);
}