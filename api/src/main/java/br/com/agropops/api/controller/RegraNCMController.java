package br.com.agropops.api.controller;

import br.com.agropops.api.model.Contador;
import br.com.agropops.api.model.RegraNCM;
import br.com.agropops.api.repository.ContadorRepository;
import br.com.agropops.api.repository.ItemNotaRepository;
import br.com.agropops.api.repository.RegraNCMRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/regras")
@CrossOrigin(origins = "*")
public class RegraNCMController {

    @Autowired
    private RegraNCMRepository regraRepository;

    @Autowired
    private ContadorRepository contadorRepository;

    // LISTAR REGRAS
    @GetMapping("/listar/{contadorId}")
    public ResponseEntity<List<RegraNCM>> listarRegras(@PathVariable Long contadorId) {
        return ResponseEntity.ok(regraRepository.findByContadorId(contadorId));
    }

    @Autowired
    private ItemNotaRepository itemNotaRepository;

    // ADICIONAR REGRA
    @PostMapping("/cadastrar/{contadorId}")
    public ResponseEntity<?> adicionarRegra(@PathVariable Long contadorId, @RequestBody RegraNCM novaRegra) {
        Optional<Contador> contadorOpt = contadorRepository.findById(contadorId);
        if (contadorOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Contador não encontrado.");
        }

        // Verifica se o contador já tem uma regra para este NCM
        if (regraRepository.findByNcmAndContadorId(novaRegra.getNcm(), contadorId).isPresent()) {
            return ResponseEntity.badRequest().body("Já existe uma regra para este NCM.");
        }

        novaRegra.setContador(contadorOpt.get());
        RegraNCM regraSalva = regraRepository.save(novaRegra);
        itemNotaRepository.aplicarRegraRetroativa(novaRegra.getNcm(), novaRegra.getIsDedutivel(), contadorId);
        return ResponseEntity.ok(regraSalva);
    }

    // EXCLUIR REGRA
    @DeleteMapping("/excluir/{id}")
    public ResponseEntity<?> excluirRegra(@PathVariable Long id) {
        if (!regraRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        regraRepository.deleteById(id);
        return ResponseEntity.ok("Regra excluída com sucesso.");
    }

    @PutMapping("/editar/{id}")
    public ResponseEntity<?> editarRegra(@PathVariable Long id, @RequestBody RegraNCM regraAtualizada) {
        Optional<RegraNCM> regraOpt = regraRepository.findById(id);
        if (regraOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        RegraNCM regra = regraOpt.get();
        // Atualiza os dados da regra existente
        regra.setNcm(regraAtualizada.getNcm());
        regra.setDescricao(regraAtualizada.getDescricao());
        regra.setIsDedutivel(regraAtualizada.getIsDedutivel());

        regraRepository.save(regra);
        itemNotaRepository.aplicarRegraRetroativa(regra.getNcm(), regra.getIsDedutivel(), regra.getContador().getId());
        return ResponseEntity.ok("Regra atualizada com sucesso.");
    }
}