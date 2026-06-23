package br.com.agropops.api.controller;

import br.com.agropops.api.dto.NotaFiscalDTO;
import br.com.agropops.api.model.NotaFiscal;
import br.com.agropops.api.repository.NotaFiscalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notas")
@CrossOrigin(origins = "*")
public class NotaFiscalController {

    // Injeta a instância do repositório
    @Autowired
    private NotaFiscalRepository notaFiscalRepository;

    @Autowired
    private br.com.agropops.api.service.SefazXmlService sefazXmlService;

    @GetMapping("/listar/{produtorId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<NotaFiscalDTO>> listarPorProdutor(
            @PathVariable Long produtorId,
            @RequestParam(required = false) String inicio, // Recebe a data inicial (opcional)
            @RequestParam(required = false) String fim     // Recebe a data final (opcional)
    ) {
        List<NotaFiscal> notas;

        // Se o React enviou as duas datas, o Java filtra pelo período:
        if (inicio != null && fim != null) {
            LocalDate dataInicio = LocalDate.parse(inicio);
            LocalDate dataFim = LocalDate.parse(fim);
            notas = notaFiscalRepository.findByProdutorIdAndDataEmissaoBetweenOrderByDataEmissaoDesc(produtorId, dataInicio, dataFim);
        }
        // Se não enviou data (ex: clicou na aba "Tudo"), o Java traz todo o histórico:
        else {
            notas = notaFiscalRepository.findByProdutorIdOrderByDataEmissaoDesc(produtorId);
        }

        // Converte as notas encontradas para o DTO (Leve)
        List<NotaFiscalDTO> listaLeve = notas.stream().map(n -> {
            NotaFiscalDTO dto = new NotaFiscalDTO();
            dto.setId(n.getId());
            dto.setNumero(n.getNumero());
            dto.setDataEmissao(n.getDataEmissao());
            dto.setTipo(n.getTipo());
            dto.setValor(n.getValor());
            dto.setIsDedutivel(n.getIsDedutivel());
            dto.setDescricao(n.getDescricao());
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(listaLeve);
    }

    @PostMapping("/importar/{produtorId}")
    public ResponseEntity<String> importarXml(
            @PathVariable Long produtorId,
            @RequestParam("arquivos") List<MultipartFile> arquivos) {

        // Manda os ficheiros para o robô ler
        int importadas = sefazXmlService.importarNotas(produtorId, arquivos);

        return ResponseEntity.ok("Sucesso! " + importadas + " novas notas foram importadas.");
    }
}