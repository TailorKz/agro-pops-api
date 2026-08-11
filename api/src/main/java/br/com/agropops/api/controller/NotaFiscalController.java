package br.com.agropops.api.controller;

import br.com.agropops.api.dto.ItemNotaDTO;
import br.com.agropops.api.dto.NotaFiscalDTO;
import br.com.agropops.api.dto.ParcelaNotaDTO;
import br.com.agropops.api.model.NotaFiscal;
import br.com.agropops.api.model.ParcelaNota;
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

    @Autowired
    private br.com.agropops.api.repository.ItemNotaRepository itemNotaRepository;

    // ENDPOINT LISTAR
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
            dto.setChaveAcesso(nota.getChaveAcesso());
            dto.setNaturezaOperacao(nota.getNaturezaOperacao());
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
                itemDTO.setCfop(item.getCfop());
                return itemDTO;
            }).collect(Collectors.toList());

            dto.setItens(itensDTO);
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(notasDTO);
    }

    // --- 2. ENDPOINT DELETAR TODAS (COM BULK DELETE DE ALTA PERFORMANCE) ---
    @DeleteMapping("/deletar-todas/{produtorId}")
    @Transactional
    public ResponseEntity<?> deletarNotasDoProdutor(
            @PathVariable Long produtorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {

        List<NotaFiscal> notas;

        if (inicio != null && fim != null) {
            notas = notaFiscalRepository.findByProdutorAndDataEmissaoBetween(produtorId, inicio, fim);
        } else {
            notas = notaFiscalRepository.findByProdutorId(produtorId);
        }

        if (notas.isEmpty()) {
            return ResponseEntity.ok("Não há notas para excluir neste período.");
        }

        // Pega apenas os IDs das notas que precisam ser apagadas
        List<Long> notaIds = notas.stream().map(NotaFiscal::getId).collect(Collectors.toList());

        // Deleta em lotes de 1.000 para não sobrecarregar a memória
        int tamanhoLote = 1000;
        for (int i = 0; i < notaIds.size(); i += tamanhoLote) {
            List<Long> loteIds = notaIds.subList(i, Math.min(i + tamanhoLote, notaIds.size()));


            notaFiscalRepository.deleteAllItensByNotaIds(loteIds);
            notaFiscalRepository.deleteAllParcelasByNotaIds(loteIds);

            notaFiscalRepository.deleteAllNotasByIds(loteIds);
        }

        return ResponseEntity.ok(notas.size() + " notas foram apagadas com sucesso de forma otimizada.");
    }


    @PostMapping("/importar/{produtorId}")
    public ResponseEntity<br.com.agropops.api.dto.ResultadoImportacaoDTO> importarXml(
            @PathVariable Long produtorId,
            @RequestParam(value = "propriedadeFallbackId", required = false) Long propriedadeFallbackId,
            @RequestParam(value = "forcar", defaultValue = "false") boolean forcar,
            @RequestParam("arquivos") List<MultipartFile> arquivos) {

        br.com.agropops.api.dto.ResultadoImportacaoDTO relatorio = sefazXmlService.importarNotas(produtorId, propriedadeFallbackId, arquivos, forcar);
        return ResponseEntity.ok(relatorio);
    }

    @PostMapping("/manual/{produtorId}")
    @Transactional
    public ResponseEntity<?> criarNotaManual(@PathVariable Long produtorId, @RequestBody br.com.agropops.api.dto.NotaManualForm form) {
        var produtorOpt = produtorRepository.findById(produtorId);
        if (produtorOpt.isEmpty()) return ResponseEntity.badRequest().body("Produtor não encontrado.");

        br.com.agropops.api.model.NotaFiscal nota = new br.com.agropops.api.model.NotaFiscal();
        nota.setNumero(form.getNumero());
        nota.setDataEmissao(form.getDataEmissao());
        nota.setTipo(form.getTipo());
        nota.setValorTotal(form.getValorTotal());
        nota.setEmpresaEnvolvida(form.getEmpresaEnvolvida());
        nota.setProdutor(produtorOpt.get());

        // Vincula a fazenda caso exista
        if (form.getPropriedadeId() != null) {
            produtorOpt.get().getPropriedades().stream()
                    .filter(p -> p.getId().equals(form.getPropriedadeId()))
                    .findFirst()
                    .ifPresent(nota::setPropriedadeRural);
        }

        // Cria 1 Item Genérico para representar a nota em memória e dedutibilidade
        br.com.agropops.api.model.ItemNota item = new br.com.agropops.api.model.ItemNota();
        item.setDescricao(form.getTipo().equals("ENTRADA") ? "Venda/Receita Lançada Manualmente" : "Despesa/Insumo Lançado Manualmente");
        item.setValor(form.getValorTotal());
        item.setIsDedutivel(form.getIsDedutivel());
        item.setNotaFiscal(nota);
        nota.getItens().add(item);

        // Vincula o parcelamento
        if (form.getParcelas() != null && !form.getParcelas().isEmpty()) {
            for (br.com.agropops.api.dto.ParcelaNotaDTO dto : form.getParcelas()) {
                br.com.agropops.api.model.ParcelaNota parcela = new br.com.agropops.api.model.ParcelaNota();
                parcela.setNumeroParcela(dto.getNumeroParcela());
                parcela.setDataVencimento(dto.getDataVencimento());
                parcela.setValor(dto.getValor());
                parcela.setNotaFiscal(nota);
                nota.getParcelas().add(parcela);
            }
        } else {
            br.com.agropops.api.model.ParcelaNota parcela = new br.com.agropops.api.model.ParcelaNota();
            parcela.setNumeroParcela("001");
            parcela.setDataVencimento(form.getDataEmissao());
            parcela.setValor(form.getValorTotal());
            parcela.setNotaFiscal(nota);
            nota.getParcelas().add(parcela);
        }

        notaFiscalRepository.save(nota);
        return ResponseEntity.ok("Documento registrado com sucesso!");
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

    @PutMapping("/item/{itemId}/toggle-dedutibilidade")
    @Transactional
    public ResponseEntity<?> toggleDedutibilidadeItem(@PathVariable Long itemId) {
        return itemNotaRepository.findById(itemId).map(item -> {
            item.setIsDedutivel(!item.getIsDedutivel());
            itemNotaRepository.save(item);
            return ResponseEntity.ok("Status alterado com sucesso.");
        }).orElse(ResponseEntity.notFound().build());
    }

    // Rota para o Modal abrir a Nota Completa
    @GetMapping("/buscar/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<NotaFiscalDTO> buscarPorId(@PathVariable Long id) {
        return notaFiscalRepository.findById(id).map(nota -> {
            NotaFiscalDTO dto = new NotaFiscalDTO();
            dto.setId(nota.getId());
            dto.setNumero(nota.getNumero());
            dto.setDataEmissao(nota.getDataEmissao());
            dto.setTipo(nota.getTipo());
            dto.setNomeEmitente(nota.getNomeEmitente());
            dto.setNomeDestinatario(nota.getNomeDestinatario());
            dto.setNomeEmitente(nota.getNomeEmitente());
            dto.setNomeDestinatario(nota.getNomeDestinatario());
            dto.setChaveAcesso(nota.getChaveAcesso());
            dto.setNaturezaOperacao(nota.getNaturezaOperacao());
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
                itemDTO.setCfop(item.getCfop());
                return itemDTO;
            }).collect(Collectors.toList());

            dto.setItens(itensDTO);
            // Mapeamento de Parcelas
            List<ParcelaNotaDTO> parcelasDTO = nota.getParcelas().stream().map(p -> {
                ParcelaNotaDTO pDTO = new ParcelaNotaDTO();
                pDTO.setId(p.getId());
                pDTO.setNumeroParcela(p.getNumeroParcela());
                pDTO.setDataVencimento(p.getDataVencimento());
                pDTO.setValor(p.getValor());
                return pDTO;
            }).collect(Collectors.toList());

            dto.setParcelas(parcelasDTO);

            return ResponseEntity.ok(dto);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/atualizar-parcelas/{notaId}")
    @Transactional
    public ResponseEntity<?> atualizarParcelas(@PathVariable Long notaId, @RequestBody List<ParcelaNotaDTO> parcelasDTO) {
        return notaFiscalRepository.findById(notaId).map(nota -> {

            // 1. Remove do banco as parcelas que o usuário deletou na tela (clicando na lixeira)
            nota.getParcelas().removeIf(p -> parcelasDTO.stream()
                    .noneMatch(dto -> dto.getId() != null && dto.getId().equals(p.getId())));

            // 2. Atualiza as existentes ou Adiciona as novas
            for (ParcelaNotaDTO dto : parcelasDTO) {
                if (dto.getId() != null) {
                    nota.getParcelas().stream()
                            .filter(p -> p.getId().equals(dto.getId()))
                            .findFirst()
                            .ifPresent(p -> {
                                p.setDataVencimento(dto.getDataVencimento());
                                p.setValor(dto.getValor()); // Permite alterar o valor
                                p.setNumeroParcela(dto.getNumeroParcela());
                            });
                } else {
                    // Se não tem ID, é uma parcela NOVA adicionada manualmente pelo contador
                    ParcelaNota nova = new ParcelaNota();
                    nova.setNumeroParcela(dto.getNumeroParcela());
                    nova.setDataVencimento(dto.getDataVencimento());
                    nova.setValor(dto.getValor());
                    nova.setNotaFiscal(nota);
                    nota.getParcelas().add(nova);
                }
            }
            notaFiscalRepository.save(nota);
            return ResponseEntity.ok("Dados atualizados com sucesso.");
        }).orElse(ResponseEntity.notFound().build());
    }
}