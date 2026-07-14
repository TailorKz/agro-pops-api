package br.com.agropops.api.controller;

import br.com.agropops.api.model.Contador;
import br.com.agropops.api.model.Produtor;
import br.com.agropops.api.repository.ContadorRepository;
import br.com.agropops.api.repository.ProdutorRepository;
import br.com.agropops.api.service.CertificadoDigitalService;
import br.com.agropops.api.service.MockDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import br.com.agropops.api.dto.ProdutorDTO;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/produtores")
@CrossOrigin(origins = "*")
public class ProdutorController {

    @Autowired
    private CertificadoDigitalService certificadoService;

    @Autowired
    private ProdutorRepository produtorRepository;

    @Autowired
    private ContadorRepository contadorRepository;

    @PostMapping("/cadastrar")
    public ResponseEntity<?> cadastrarProdutor(
            @RequestParam("nome") String nome,
            @RequestParam("cpfCnpj") String cpfCnpj,
            @RequestParam(value = "cnpj", required = false) String cnpj, // <-- NOVO PARÂMETRO
            @RequestParam("inscricaoEstadual") String inscricaoEstadual,
            @RequestParam("contadorId") Long contadorId,
            @RequestParam(value = "senhaCertificado", required = false) String senhaCertificado,
            @RequestParam(value = "certificado", required = false) MultipartFile certificado) {

        try {
            Optional<Contador> contadorOpt = contadorRepository.findById(contadorId);
            if (contadorOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Erro: Contador não encontrado.");
            }

            Produtor produtor = new Produtor();
            produtor.setNome(nome);
            produtor.setCpfCnpj(cpfCnpj);
            produtor.setCnpj(cnpj); // <-- SALVANDO O CNPJ
            produtor.setInscricaoEstadual(inscricaoEstadual);
            produtor.setSenhaCertificado(senhaCertificado);
            produtor.setContador(contadorOpt.get());

            // Transforma o ficheiro .pfx numa matriz de bytes e guarda no objeto
            if (certificado != null && !certificado.isEmpty()) {
                byte[] bytesCertificado = certificado.getBytes();
                produtor.setCertificadoPfx(bytesCertificado);

                try {
                    Date validade = certificadoService.extrairValidade(bytesCertificado, senhaCertificado);
                    produtor.setValidadeCertificado(validade);
                    System.out.println("✅ Certificado válido! Expira em: " + validade);
                } catch (Exception e) {
                    // Bloqueia o registo e avisa o ecrã do React que a senha está errada
                    return ResponseEntity.badRequest().body(e.getMessage());
                }
            }

            // Salva no banco de dados
            Produtor salvo = produtorRepository.save(produtor);

            // mockDataService.gerarNotasFalsasParaProdutor(salvo);

            return ResponseEntity.ok(salvo);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro interno ao processar o arquivo: " + e.getMessage());
        }
    }

    // ROTA 2: Listar todos os Produtores do Contador Logado
    @GetMapping("/listar/{contadorId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProdutorDTO>> listarPorContador(@PathVariable Long contadorId) {
        List<Produtor> produtores = produtorRepository.findByContadorId(contadorId);
        List<ProdutorDTO> listaLeve = produtores.stream().map(p -> {
            ProdutorDTO dto = new ProdutorDTO();
            dto.setId(p.getId());
            dto.setNome(p.getNome());
            dto.setCpfCnpj(p.getCpfCnpj());
            dto.setCnpj(p.getCnpj());
            dto.setInscricaoEstadual(p.getInscricaoEstadual());
            dto.setValidadeCertificado(p.getValidadeCertificado());
            return dto;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(listaLeve);
    }

    // ROTA 3: Login exclusivo do App Mobile (Produtor)
    @PostMapping("/login-mobile")
    @Transactional(readOnly = true)
    public ResponseEntity<?> loginMobile(@RequestBody Map<String, String> dados) {
        String cpfCnpj = dados.get("cpfCnpj");
        // Remove pontos, traços e barras (caso o app envie com máscara)
        String cpfLimpo = cpfCnpj.replaceAll("[^0-9]", "");

        Optional<Produtor> produtorOpt = produtorRepository.findFirstByCpfCnpj(cpfLimpo);

        if (produtorOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Produtor não encontrado com este documento.");
        }

        Produtor produtor = produtorOpt.get();

        // Gera o "Crachá" (JWT) usando o CPF como identidade (Subject)
        Algorithm algoritmo = Algorithm.HMAC256("MinhaChaveSuperSecretaDoAgroContabil");
        String token = JWT.create()
                .withIssuer("AgroPops API")
                .withSubject(produtor.getCpfCnpj())
                .withExpiresAt(java.time.Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .sign(algoritmo);

        // Devolve o Crachá e os dados do Produtor para o telemóvel
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("token", token);
        resposta.put("produtor", produtor);

        return ResponseEntity.ok(resposta);
    }
}