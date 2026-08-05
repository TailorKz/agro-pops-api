package br.com.agropops.api.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class NotaManualForm {
    private String tipo;
    private LocalDate dataEmissao;
    private String empresaEnvolvida;
    private String cpfCnpj;
    private String numero;
    private BigDecimal valorTotal;
    private Boolean isDedutivel;
    private Long propriedadeId;
    private List<ParcelaNotaDTO> parcelas;
}