package br.com.agropops.api.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ResultadoImportacaoDTO {
    private int importadas = 0;
    private int ignoradas = 0;
    private int falhas = 0;
    private List<NotaDivergenteDTO> divergentes = new ArrayList<>();
}