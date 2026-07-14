package br.com.agropops.api.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class NotaFiscalDTO {
    private Long id;
    private String numero;
    private LocalDate dataEmissao;
    private String tipo;
    private BigDecimal valorTotal;
    private String empresaEnvolvida;
    private String chaveAcessoReferencia;
    private List<ItemNotaDTO> itens;
}