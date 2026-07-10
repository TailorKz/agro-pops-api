package br.com.agropops.api.controller;

import br.com.agropops.api.dto.LancamentoDTO;
import br.com.agropops.api.dto.LancamentoAvulsoForm;
import br.com.agropops.api.model.LancamentoAvulso;
import br.com.agropops.api.model.Produtor;
import br.com.agropops.api.repository.LancamentoAvulsoRepository;
import br.com.agropops.api.repository.ProdutorRepository;
import br.com.agropops.api.service.LivroCaixaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/livro-caixa")
public class LivroCaixaController {

    @Autowired
    private LivroCaixaService livroCaixaService;

    @Autowired
    private LancamentoAvulsoRepository avulsoRepository;

    @Autowired
    private ProdutorRepository produtorRepository;

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
        // Segurança: Se for ENTRADA, nunca é dedutível do imposto
        avulso.setIsDedutivel(!form.tipo().equals("ENTRADA") && form.isDedutivel());
        avulso.setProdutor(produtor);

        avulsoRepository.save(avulso);

        return ResponseEntity.ok("Lançamento avulso registrado com sucesso no Livro Caixa.");
    }
}