package br.com.agropops.api.repository;

import br.com.agropops.api.model.ItemNota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ItemNotaRepository extends JpaRepository<ItemNota, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE ItemNota i SET i.isDedutivel = :isDedutivel WHERE i.ncm = :ncm AND i.notaFiscal.produtor.contador.id = :contadorId")
    void aplicarRegraRetroativa(String ncm, Boolean isDedutivel, Long contadorId);
}