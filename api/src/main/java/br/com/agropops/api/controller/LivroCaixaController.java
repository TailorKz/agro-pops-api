package br.com.agropops.api.controller;

import br.com.agropops.api.dto.LancamentoDTO;
import br.com.agropops.api.dto.LancamentoAvulsoForm;
import br.com.agropops.api.dto.TotaisLivroCaixaDTO;
import br.com.agropops.api.model.LancamentoAvulso;
import br.com.agropops.api.model.Produtor;
import br.com.agropops.api.model.PropriedadeRural;
import br.com.agropops.api.repository.LancamentoAvulsoRepository;
import br.com.agropops.api.repository.ProdutorRepository;
import br.com.agropops.api.repository.PropriedadeRuralRepository;
import br.com.agropops.api.service.LivroCaixaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/livro-caixa")
public class LivroCaixaController {

    @Autowired
    private LivroCaixaService livroCaixaService;

    @Autowired
    private LancamentoAvulsoRepository avulsoRepository;

    @Autowired
    private ProdutorRepository produtorRepository;

    @Autowired
    private PropriedadeRuralRepository propriedadeRuralRepository; // <-- REPOSITÓRIO CORRETO

    @GetMapping("/{produtorId}")
    public ResponseEntity<List<LancamentoDTO>> getLivroCaixa(
            @PathVariable Long produtorId,
            @RequestParam int ano) {
        List<LancamentoDTO> lancamentos = livroCaixaService.buscarLivroCaixa(produtorId, ano);
        return ResponseEntity.ok(lancamentos);
    }

    @PostMapping("/{produtorId}/avulso")
    public ResponseEntity<String> cadastrarLancamentoAvulso(
            @PathVariable Long produtorId,
            @RequestBody LancamentoAvulsoForm form) {

        Produtor produtor = produtorRepository.findById(produtorId)
                .orElseThrow(() -> new RuntimeException("Produtor não encontrado"));

        LancamentoAvulso avulso = new LancamentoAvulso();
        avulso.setData(form.data());
        avulso.setTipoDocumento(form.tipoDocumento());
        avulso.setDocumento(form.documento());
        avulso.setCpfCnpjParticipante(form.cpfCnpjParticipante());
        avulso.setHistorico(form.historico());
        avulso.setTipo(form.tipo());
        avulso.setValor(form.valor());
        avulso.setIsDedutivel(!form.tipo().equals("ENTRADA") && form.isDedutivel());
        avulso.setProdutor(produtor);

        // --- AMARRAÇÃO COM A FAZENDA SELECIONADA ---
        if (form.propriedadeId() != null) {
            PropriedadeRural propriedade = propriedadeRuralRepository.findById(form.propriedadeId()).orElse(null);
            avulso.setPropriedadeRural(propriedade);
        }

        avulsoRepository.save(avulso);
        return ResponseEntity.ok("Lançamento avulso registrado com sucesso no Livro Caixa.");
    }

    @GetMapping("/{produtorId}/totais")
    public ResponseEntity<TotaisLivroCaixaDTO> getTotaisLivroCaixa(
            @PathVariable Long produtorId,
            @RequestParam int ano) {
        TotaisLivroCaixaDTO totais = livroCaixaService.calcularTotais(produtorId, ano);
        return ResponseEntity.ok(totais);
    }

    @DeleteMapping("/avulso/{id}")
    public ResponseEntity<String> deletarLancamentoAvulso(@PathVariable Long id) {
        if (!avulsoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        avulsoRepository.deleteById(id);
        return ResponseEntity.ok("Lançamento avulso removido com sucesso.");
    }
}