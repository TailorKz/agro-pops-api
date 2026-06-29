package br.com.agropops.api.controller;

import br.com.agropops.api.dto.ItemNotaDTO;
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
    public ResponseEntity<List<NotaFiscalDTO>> listarNotas(
            @PathVariable Long produtorId,
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fim) {

        // Aqui você busca as notas no repositório (depende de como estava o seu código, mantive a lógica padrão)
        List<NotaFiscal> notas = notaFiscalRepository.findByProdutorId(produtorId);

        // Converte as entidades para DTO (O empacotamento)
        List<NotaFiscalDTO> notasDTO = notas.stream().map(nota -> {
            NotaFiscalDTO dto = new NotaFiscalDTO();
            dto.setId(nota.getId());
            dto.setNumero(nota.getNumero());
            dto.setDataEmissao(nota.getDataEmissao());
            dto.setTipo(nota.getTipo());
            dto.setValorTotal(nota.getValorTotal());
            dto.setEmpresaEnvolvida(nota.getEmpresaEnvolvida());

            // Converte os Itens
            List<ItemNotaDTO> itensDTO = nota.getItens().stream().map(item -> {
                ItemNotaDTO itemDTO = new ItemNotaDTO();
                itemDTO.setId(item.getId());
                itemDTO.setDescricao(item.getDescricao());
                itemDTO.setNcm(item.getNcm());
                itemDTO.setValor(item.getValor());
                itemDTO.setIsDedutivel(item.getIsDedutivel());
                return itemDTO;
            }).collect(Collectors.toList());

            dto.setItens(itensDTO);
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(notasDTO);
    }

    @PostMapping("/importar/{produtorId}")
    public ResponseEntity<String> importarXml(
            @PathVariable Long produtorId,
            @RequestParam("arquivos") List<MultipartFile> arquivos) {

        // Manda os ficheiros para o robô ler
        int importadas = sefazXmlService.importarNotas(produtorId, arquivos);

        return ResponseEntity.ok("Sucesso! " + importadas + " novas notas foram importadas.");
    }

    @Autowired
    private br.com.agropops.api.service.SefazSyncService sefazSyncService;

    @Autowired
    private br.com.agropops.api.repository.ProdutorRepository produtorRepository;

    @PostMapping("/manifestar/{produtorId}/{chaveAcesso}")
    public ResponseEntity<String> manifestarNota(
            @PathVariable Long produtorId,
            @PathVariable String chaveAcesso,
            @RequestParam("tipo") String tipoAcao) { // Ex: "CONFIRMAR" ou "DESCONHECER"

        var produtorOpt = produtorRepository.findById(produtorId);
        if (produtorOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Produtor não encontrado.");
        }

        // Importe a classe do NFTipoEventoManifestacaoDestinatario no topo do Controller
        com.fincatto.documentofiscal.nfe400.classes.evento.manifestacaodestinatario.NFTipoEventoManifestacaoDestinatario tipoEvento;

        switch (tipoAcao.toUpperCase()) {
            case "CONFIRMAR":
                tipoEvento = com.fincatto.documentofiscal.nfe400.classes.evento.manifestacaodestinatario.NFTipoEventoManifestacaoDestinatario.CONFIRMACAO_DA_OPERACAO;
                break;
            case "DESCONHECER":
                tipoEvento = com.fincatto.documentofiscal.nfe400.classes.evento.manifestacaodestinatario.NFTipoEventoManifestacaoDestinatario.DESCONHECIMENTO_DA_OPERACAO;
                break;
            default:
                return ResponseEntity.badRequest().body("Ação inválida.");
        }

        String resultado = sefazSyncService.manifestarNotaManualmente(produtorOpt.get(), chaveAcesso, tipoEvento);
        return ResponseEntity.ok(resultado);
    }
}