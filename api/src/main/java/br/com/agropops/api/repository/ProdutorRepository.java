package br.com.agropops.api.repository;

import br.com.agropops.api.model.Produtor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProdutorRepository extends JpaRepository<Produtor, Long> {

    // Procura todos os produtores que pertencem a este Contador
    List<Produtor> findByContadorId(Long contadorId);

    java.util.Optional<Produtor> findFirstByCpfCnpj(String cpfCnpj);
}