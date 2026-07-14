package br.com.agropops.api.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.Date;

@Getter
@Setter
public class ProdutorDTO {
    private Long id;
    private String nome;
    private String cpfCnpj;
    private String cnpj;
    private String inscricaoEstadual;
    private Date validadeCertificado;
}