package br.com.agropops.api.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ItemNotaDTO {
    private Long id;
    private String descricao;
    private String ncm;
    private BigDecimal valor;
    private Boolean isDedutivel;
}