package br.com.agropops.api.dto;

import java.math.BigDecimal;

public record TotaisLivroCaixaDTO(
        BigDecimal totalReceitas,
        BigDecimal totalDespesasDedutiveis
) {}