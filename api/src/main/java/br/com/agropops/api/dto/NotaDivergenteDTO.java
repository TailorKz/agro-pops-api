package br.com.agropops.api.dto;

import lombok.Data;

@Data
public class NotaDivergenteDTO {
    private String nomeArquivo;
    private String emitente;
    private String destinatario;
    private String documentoEncontrado;
}