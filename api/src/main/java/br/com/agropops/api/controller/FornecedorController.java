package br.com.agropops.api.controller;

import br.com.agropops.api.model.Contador;
import br.com.agropops.api.model.Fornecedor;
import br.com.agropops.api.repository.ContadorRepository;
import br.com.agropops.api.repository.FornecedorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/fornecedores")
@CrossOrigin(origins = "*")
public class FornecedorController {

    @Autowired
    private FornecedorRepository fornecedorRepository;

    @Autowired
    private ContadorRepository contadorRepository;

    @GetMapping("/listar/{contadorId}")
    public ResponseEntity<List<Fornecedor>> listar(@PathVariable Long contadorId) {
        return ResponseEntity.ok(fornecedorRepository.findByContadorId(contadorId));
    }

    @PostMapping("/cadastrar/{contadorId}")
    public ResponseEntity<?> cadastrar(@PathVariable Long contadorId, @RequestBody Fornecedor fornecedor) {
        Optional<Contador> contadorOpt = contadorRepository.findById(contadorId);
        if (contadorOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Contador não encontrado.");
        }
        fornecedor.setContador(contadorOpt.get());
        return ResponseEntity.ok(fornecedorRepository.save(fornecedor));
    }

    @DeleteMapping("/deletar/{id}")
    public ResponseEntity<?> deletar(@PathVariable Long id) {
        if (!fornecedorRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        fornecedorRepository.deleteById(id);
        return ResponseEntity.ok("Fornecedor excluído com sucesso.");
    }
}