package br.com.agropops.api.controller;

import br.com.agropops.api.model.Contador;
import br.com.agropops.api.model.Produtor;
import br.com.agropops.api.repository.ContadorRepository;
import br.com.agropops.api.repository.ProdutorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/produtores")
@CrossOrigin(origins = "*")
public class ProdutorController {

    @Autowired
    private ProdutorRepository produtorRepository;

    @Autowired
    private ContadorRepository contadorRepository;

    @PostMapping("/cadastrar")
    @Transactional // POSTGRES GRAVAR O LOB
    public ResponseEntity<?> cadastrarProdutor(
            @RequestParam("nome") String nome,
            @RequestParam("cpfCnpj") String cpfCnpj,
            @RequestParam("inscricaoEstadual") String inscricaoEstadual,
            @RequestParam("senhaCertificado") String senhaCertificado,
            @RequestParam("contadorId") Long contadorId,
            @RequestParam(value = "certificado", required = false) MultipartFile certificado) {

        try {
            // Encontra o Contador dono deste produtor
            Optional<Contador> contadorOpt = contadorRepository.findById(contadorId);
            if (contadorOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Erro: Contador não encontrado.");
            }

            // Preenche os dados do Produtor
            Produtor produtor = new Produtor();
            produtor.setNome(nome);
            produtor.setCpfCnpj(cpfCnpj);
            produtor.setInscricaoEstadual(inscricaoEstadual);
            produtor.setSenhaCertificado(senhaCertificado);
            produtor.setContador(contadorOpt.get());

            // Transforma o ficheiro .pfx numa matriz de bytes e guarda no objeto
            if (certificado != null && !certificado.isEmpty()) {
                produtor.setCertificadoPfx(certificado.getBytes());
            }

            // Salva no banco de dados (Railway)
            Produtor salvo = produtorRepository.save(produtor);
            return ResponseEntity.ok(salvo);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao processar o certificado: " + e.getMessage());
        }
    }

    // ROTA 2: Listar todos os Produtores do Contador Logado
    @GetMapping("/listar/{contadorId}")
    @Transactional(readOnly = true) // POSTGRES LER O LOB
    public ResponseEntity<List<Produtor>> listarPorContador(@PathVariable Long contadorId) {
        List<Produtor> produtores = produtorRepository.findByContadorId(contadorId);
        return ResponseEntity.ok(produtores);
    }
}