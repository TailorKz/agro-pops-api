package br.com.agropops.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LancamentoDTO(
        String id,
        LocalDate data,
        String documento,
        String historico,
        String origem,
        String tipo,
        BigDecimal valor,
        Boolean isDedutivel
) {}