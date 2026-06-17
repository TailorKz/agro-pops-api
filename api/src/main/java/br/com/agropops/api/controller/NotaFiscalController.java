package br.com.agropops.api.controller;

import br.com.agropops.api.model.NotaFiscal;
import br.com.agropops.api.repository.NotaFiscalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional; // <-- IMPORTAÇÃO AQUI
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notas")
@CrossOrigin(origins = "*")
public class NotaFiscalController {

    @Autowired
    private NotaFiscalRepository repository;

    @GetMapping("/listar/{produtorId}")
    @Transactional(readOnly = true) // <-- A MÁGICA PARA O POSTGRES NÃO BLOQUEAR A LEITURA
    public ResponseEntity<List<NotaFiscal>> listarPorProdutor(@PathVariable Long produtorId) {
        List<NotaFiscal> notas = repository.findByProdutorIdOrderByDataEmissaoDesc(produtorId);
        return ResponseEntity.ok(notas);
    }
}