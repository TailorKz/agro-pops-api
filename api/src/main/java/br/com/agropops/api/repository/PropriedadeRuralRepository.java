package br.com.agropops.api.repository;

import br.com.agropops.api.model.PropriedadeRural;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PropriedadeRuralRepository extends JpaRepository<PropriedadeRural, Long> {
    List<PropriedadeRural> findByProdutorId(Long produtorId);
    Optional<PropriedadeRural> findFirstByInscricaoEstadual(String inscricaoEstadual);
}