package br.com.agropops.api.controller;

import br.com.agropops.api.dto.PropriedadeRuralDTO;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

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
            @RequestParam(value = "cnpj", required = false) String cnpj,
            @RequestParam(value = "telefone", required = false) String telefone,
            @RequestParam("contadorId") Long contadorId,
            @RequestParam("propriedades") String propriedadesJson, // <-- RECEBE A LISTA COMO TEXTO JSON
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
            produtor.setCnpj(cnpj);
            produtor.setTelefone(telefone);
            produtor.setContador(contadorOpt.get());

            // --- TRUQUE DE MESTRE: CONVERTER O JSON PARA A LISTA DE PROPRIEDADES ---
            ObjectMapper mapper = new ObjectMapper();
            List<br.com.agropops.api.model.PropriedadeRural> listaPropriedades =
                    mapper.readValue(propriedadesJson, new TypeReference<List<br.com.agropops.api.model.PropriedadeRural>>(){});

            // Vincula o produtor a cada uma das propriedades criadas
            for (br.com.agropops.api.model.PropriedadeRural p : listaPropriedades) {
                p.setProdutor(produtor);
            }
            produtor.setPropriedades(listaPropriedades);

            // Transforma o arquivo .pfx em bytes e processa a senha (Mantido igual)
            if (certificado != null && !certificado.isEmpty()) {
                byte[] bytesCertificado = certificado.getBytes();
                produtor.setCertificadoPfx(bytesCertificado);
                produtor.setSenhaCertificado(senhaCertificado);
                try {
                    Date validade = certificadoService.extrairValidade(bytesCertificado, senhaCertificado);
                    produtor.setValidadeCertificado(validade);
                    System.out.println("✅ Certificado válido! Expira em: " + validade);
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body("Senha do certificado incorreta ou arquivo inválido.");
                }
            }

            Produtor salvo = produtorRepository.save(produtor);
            return ResponseEntity.ok(salvo);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro interno: " + e.getMessage());
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
            dto.setTelefone(p.getTelefone());
            dto.setValidadeCertificado(p.getValidadeCertificado());

            // Converte a lista do Banco para DTO
            List<PropriedadeRuralDTO> propsDTO = p.getPropriedades().stream().map(prop -> {
                PropriedadeRuralDTO propDto = new PropriedadeRuralDTO();
                propDto.setId(prop.getId());
                propDto.setNome(prop.getNome());
                propDto.setInscricaoEstadual(prop.getInscricaoEstadual());
                propDto.setCaepf(prop.getCaepf());
                propDto.setPercentualParticipacao(prop.getPercentualParticipacao());
                return propDto;
            }).collect(java.util.stream.Collectors.toList());

            dto.setPropriedades(propsDTO);

            return dto;
        }).collect(java.util.stream.Collectors.toList());

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