package br.com.agropops.api.repository;

import br.com.agropops.api.model.RegraNCM;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RegraNCMRepository extends JpaRepository<RegraNCM, Long> {

    // Para listar as regras na tela do contador
    List<RegraNCM> findByContadorId(Long contadorId);

    // Para o Robô da SEFAZ pesquisar a regra
    Optional<RegraNCM> findByNcmAndContadorId(String ncm, Long contadorId);
}