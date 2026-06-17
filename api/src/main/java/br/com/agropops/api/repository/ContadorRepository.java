package br.com.agropops.api.repository;

import br.com.agropops.api.model.Contador;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ContadorRepository extends JpaRepository<Contador, Long> {

    // cria o comando SQL "SELECT * FROM contadores WHERE email = ?" automaticamente
    Optional<Contador> findByEmail(String email);
}