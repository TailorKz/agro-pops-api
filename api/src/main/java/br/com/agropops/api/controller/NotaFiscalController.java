package br.com.agropops.api.controller;

import br.com.agropops.api.dto.ItemNotaDTO;
import br.com.agropops.api.dto.NotaFiscalDTO;
import br.com.agropops.api.model.NotaFiscal;
import br.com.agropops.api.repository.NotaFiscalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notas")
@CrossOrigin(origins = "*")
public class NotaFiscalController {

    @Autowired
    private NotaFiscalRepository notaFiscalRepository;

    @Autowired
    private br.com.agropops.api.service.SefazXmlService sefazXmlService;

    @Autowired
    private br.com.agropops.api.service.SefazSyncService sefazSyncService;

    @Autowired
    private br.com.agropops.api.repository.ProdutorRepository produtorRepository;

    // --- 1. ENDPOINT LISTAR (AGORA FILTRANDO DATAS CORRETAMENTE) ---
    @Transactional(readOnly = true)
    @GetMapping("/listar/{produtorId}")
    public ResponseEntity<List<NotaFiscalDTO>> listarNotas(
            @PathVariable Long produtorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        List<NotaFiscal> notas;

        // SE O REACT MANDOU DATA, BUSCA ENTRE AS DATAS. SENÃO, BUSCA TUDO.
        if (inicio != null && fim != null) {
            notas = notaFiscalRepository.findByProdutorIdAndDataEmissaoBetweenOrderByDataEmissaoDesc(produtorId, inicio, fim);
        } else {
            notas = notaFiscalRepository.findByProdutorIdOrderByDataEmissaoDesc(produtorId);
        }

        List<NotaFiscalDTO> notasDTO = notas.stream().map(nota -> {
            NotaFiscalDTO dto = new NotaFiscalDTO();
            dto.setId(nota.getId());
            dto.setNumero(nota.getNumero());
            dto.setDataEmissao(nota.getDataEmissao());
            dto.setTipo(nota.getTipo());
            dto.setValorTotal(nota.getValorTotal());
            dto.setEmpresaEnvolvida(nota.getEmpresaEnvolvida());
            dto.setChaveAcessoReferencia(nota.getChaveAcessoReferencia());

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

    // --- 2. ENDPOINT DELETAR TODAS (FILTRANDO POR DATA) ---
    @DeleteMapping("/deletar-todas/{produtorId}")
    @Transactional
    public ResponseEntity<?> deletarNotasDoProdutor(
            @PathVariable Long produtorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        List<NotaFiscal> notas;

        if (inicio != null && fim != null) {
            notas = notaFiscalRepository.findByProdutorIdAndDataEmissaoBetweenOrderByDataEmissaoDesc(produtorId, inicio, fim);
        } else {
            // Se não veio data, apaga TUDO do produtor
            notas = notaFiscalRepository.findByProdutorId(produtorId);
        }

        if (notas.isEmpty()) {
            return ResponseEntity.ok("Não há notas para excluir neste período.");
        }

        notaFiscalRepository.deleteAll(notas);
        return ResponseEntity.ok(notas.size() + " notas foram apagadas com sucesso.");
    }

    // --- DEMAIS ENDPOINTS CONTINUAM IGUAIS ---

    @PostMapping("/importar/{produtorId}")
    public ResponseEntity<String> importarXml(
            @PathVariable Long produtorId,
            @RequestParam("arquivos") List<MultipartFile> arquivos) {

        int importadas = sefazXmlService.importarNotas(produtorId, arquivos);
        return ResponseEntity.ok("Sucesso! " + importadas + " novas notas foram importadas.");
    }

    @PostMapping("/manifestar/{produtorId}/{chaveAcesso}")
    public ResponseEntity<String> manifestarNota(
            @PathVariable Long produtorId,
            @PathVariable String chaveAcesso,
            @RequestParam("tipo") String tipoAcao) {

        var produtorOpt = produtorRepository.findById(produtorId);
        if (produtorOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Produtor não encontrado.");
        }

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

    @PutMapping("/atualizar-itens/{notaId}")
    @Transactional
    public ResponseEntity<?> atualizarItensDaNota(@PathVariable Long notaId, @RequestBody List<ItemNotaDTO> itensAtualizados) {
        Optional<NotaFiscal> notaOpt = notaFiscalRepository.findById(notaId);
        if (notaOpt.isPresent()) {
            NotaFiscal nota = notaOpt.get();
            for (ItemNotaDTO dto : itensAtualizados) {
                nota.getItens().stream()
                        .filter(item -> item.getId().equals(dto.getId()))
                        .findFirst()
                        .ifPresent(item -> item.setIsDedutivel(dto.getIsDedutivel()));
            }
            notaFiscalRepository.save(nota);
            return ResponseEntity.ok("Itens salvos com sucesso!");
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/deletar/{id}")
    @Transactional
    public ResponseEntity<?> deletarNota(@PathVariable Long id) {
        notaFiscalRepository.deleteById(id);
        return ResponseEntity.ok("Nota excluída com sucesso.");
    }
}