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

@RestController
@RequestMapping("/api/produtores")
@CrossOrigin(origins = "*")
public class ProdutorController {

    @Autowired
    private CertificadoDigitalService certificadoService;

    @Autowired
    private MockDataService mockDataService;

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

        // O bloco TRY principal que o Java exige para lidar com a IOException do getBytes()
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
                byte[] bytesCertificado = certificado.getBytes(); // Agora o try lá em cima trata isso!
                produtor.setCertificadoPfx(bytesCertificado);

                // --- NOVA VALIDAÇÃO DO CERTIFICADO ---
                try {
                    Date validade = certificadoService.extrairValidade(bytesCertificado, senhaCertificado);
                    produtor.setValidadeCertificado(validade);
                    System.out.println("✅ Certificado válido! Expira em: " + validade);
                } catch (Exception e) {
                    // Bloqueia o registo e avisa o ecrã do React que a senha está errada!
                    return ResponseEntity.badRequest().body(e.getMessage());
                }
                // --------------------------------------
            }

            // Salva no banco de dados (Railway)
            Produtor salvo = produtorRepository.save(produtor);

            mockDataService.gerarNotasFalsasParaProdutor(salvo);

            // Faltava este retorno de sucesso!
            return ResponseEntity.ok(salvo);

        } catch (Exception e) {
            // Faltava fechar o catch principal que captura a IOException do getBytes()
            return ResponseEntity.internalServerError().body("Erro interno ao processar o arquivo: " + e.getMessage());
        }
    } // Faltava fechar a chave do método cadastrarProdutor!

    // ROTA 2: Listar todos os Produtores do Contador Logado
    @GetMapping("/listar/{contadorId}")
    @Transactional(readOnly = true) // POSTGRES LER O LOB
    public ResponseEntity<List<Produtor>> listarPorContador(@PathVariable Long contadorId) {
        List<Produtor> produtores = produtorRepository.findByContadorId(contadorId);
        return ResponseEntity.ok(produtores);
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
                // --- A NOVA LINHA DE VALIDADE ABAIXO ---
                .withExpiresAt(java.time.Instant.now().plus(30, java.time.temporal.ChronoUnit.DAYS))
                .sign(algoritmo);

        // Devolve o Crachá e os dados do Produtor para o telemóvel
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("token", token);
        resposta.put("produtor", produtor);

        return ResponseEntity.ok(resposta);
    }
}