package br.com.agropops.api.repository;

import br.com.agropops.api.model.Fornecedor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FornecedorRepository extends JpaRepository<Fornecedor, Long> {
    List<Fornecedor> findByContadorId(Long contadorId);
}