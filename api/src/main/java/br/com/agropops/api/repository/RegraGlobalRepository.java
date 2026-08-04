package br.com.agropops.api.repository;

import br.com.agropops.api.model.RegraGlobal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RegraGlobalRepository extends JpaRepository<RegraGlobal, Long> {
    Optional<RegraGlobal> findByTipoAndCodigo(String tipo, String codigo);
}