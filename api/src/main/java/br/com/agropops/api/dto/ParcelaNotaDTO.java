package br.com.agropops.api.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ParcelaNotaDTO {
    private Long id;
    private String numeroParcela;
    private LocalDate dataVencimento;
    private BigDecimal valor;
}