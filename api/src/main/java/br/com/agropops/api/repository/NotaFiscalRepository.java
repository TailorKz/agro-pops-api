package br.com.agropops.api.repository;

import br.com.agropops.api.model.NotaFiscal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, Long> {

    // Método antigo (Traz tudo do produtor)
    List<NotaFiscal> findByProdutorIdOrderByDataEmissaoDesc(Long produtorId);

    List<NotaFiscal> findByProdutorId(Long produtorId);

    // Filtra as notas por um período de datas
    List<NotaFiscal> findByProdutorIdAndDataEmissaoBetweenOrderByDataEmissaoDesc(Long produtorId, LocalDate dataInicio, LocalDate dataFim);

    // Verifica se a nota já existe na base de dados pela Chave de 44 dígitos
    boolean existsByChaveAcesso(String chaveAcesso);
}