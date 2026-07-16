package br.com.agropops.api.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class ProdutorDTO {
    private Long id;
    private String nome;
    private String cpfCnpj;
    private String cnpj;
    private String telefone;
    private Date validadeCertificado;

    // LISTA DE PROPRIEDADES
    private List<PropriedadeRuralDTO> propriedades;
}