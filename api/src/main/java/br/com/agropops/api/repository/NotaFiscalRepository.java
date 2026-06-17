package br.com.agropops.api.repository;

import br.com.agropops.api.model.NotaFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, Long> {

    // O Spring gera o SQL: SELECT * FROM notas_fiscais WHERE produtor_id = ? ORDER BY data_emissao DESC
    List<NotaFiscal> findByProdutorIdOrderByDataEmissaoDesc(Long produtorId);
}