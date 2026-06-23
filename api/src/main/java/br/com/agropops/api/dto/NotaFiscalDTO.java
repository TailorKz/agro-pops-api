package br.com.agropops.api.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class NotaFiscalDTO {
    private Long id;
    private String numero;
    private LocalDate dataEmissao;
    private String tipo;
    private BigDecimal valor;
    private Boolean isDedutivel;
    private String descricao;
}