package br.com.agropops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LancamentoAvulsoForm(
        LocalDate data,
        String tipoDocumento,
        String documento,
        String cpfCnpjParticipante,
        String historico,
        String tipo,
        BigDecimal valor,
        Boolean isDedutivel,
        Long propriedadeId
) {}