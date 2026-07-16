package br.com.agropops.api.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PropriedadeRuralDTO {
    private Long id;
    private String nome;
    private String inscricaoEstadual;
    private String caepf;
    private BigDecimal percentualParticipacao;
}