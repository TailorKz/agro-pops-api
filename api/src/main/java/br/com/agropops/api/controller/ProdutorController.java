package br.com.agropops.api.controller;

import br.com.agropops.api.dto.PropriedadeRuralDTO;
import br.com.agropops.api.model.Contador;
import br.com.agropops.api.model.Produtor;
import br.com.agropops.api.model.PropriedadeRural;
import br.com.agropops.api.repository.ContadorRepository;
import br.com.agropops.api.repository.ProdutorRepository;
import br.com.agropops.api.security.TokenService;
import br.com.agropops.api.service.CertificadoDigitalService;
import br.com.agropops.api.dto.ProdutorDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private ProdutorRepository produtorRepository;

    @Autowired
    private ContadorRepository contadorRepository;

    // Injeção do nosso serviço de token
    @Autowired
    private TokenService tokenService;

    @Autowired
    private br.com.agropops.api.repository.NotaFiscalRepository notaRepository;

    @Autowired
    private br.com.agropops.api.repository.LancamentoAvulsoRepository avulsoRepository;

    @PostMapping("/cadastrar")
    @Transactional
    public ResponseEntity<?> cadastrarProdutor(
            @RequestParam(value = "id", required = false) String idStr,
            @RequestParam("nome") String nome,
            @RequestParam("cpfCnpj") String cpfCnpj,
            @RequestParam(value = "cnpj", required = false) String cnpj,
            @RequestParam(value = "telefone", required = false) String telefone,
            @RequestParam(value = "endereco", required = false) String endereco,
            @RequestParam("contadorId") Long contadorId,
            @RequestParam("propriedades") String propriedadesJson,
            @RequestParam(value = "senhaCertificado", required = false) String senhaCertificado,
            @RequestParam(value = "certificado", required = false) MultipartFile certificado) {
        try {
            Long id = null;
            if (idStr != null && !idStr.isBlank() && !idStr.equals("undefined") && !idStr.equals("null")) {
                try {
                    id = Long.valueOf(idStr);
                } catch (NumberFormatException ignored) {}
            }

            Optional<Contador> contadorOpt = contadorRepository.findById(contadorId);
            if (contadorOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Erro: Contador não encontrado.");
            }

            Produtor produtor;
            ObjectMapper mapper = new ObjectMapper();
            List<PropriedadeRural> listaNovasPropriedades = mapper.readValue(propriedadesJson, new TypeReference<List<PropriedadeRural>>(){});

            if (id != null) {
                produtor = produtorRepository.findById(id).orElseThrow(() -> new RuntimeException("Produtor não encontrado"));

                produtor.setNome(nome);
                produtor.setCpfCnpj(cpfCnpj);
                produtor.setCnpj(cnpj);
                produtor.setTelefone(telefone);
                produtor.setEndereco(endereco);
                produtor.setContador(contadorOpt.get());

                List<PropriedadeRural> propriedadesAtuais = produtor.getPropriedades();

                // 1. Identificar quais propriedades o usuário deletou na tela
                List<PropriedadeRural> propriedadesParaRemover = propriedadesAtuais.stream()
                        .filter(propAtual -> listaNovasPropriedades.stream()
                                .noneMatch(pNovo -> pNovo.getId() != null && pNovo.getId().equals(propAtual.getId())))
                        .collect(java.util.stream.Collectors.toList());

                // 2. Validação de Integridade (Aviso bloqueador para o Contador)
                for (PropriedadeRural prop : propriedadesParaRemover) {
                    boolean temNotas = notaRepository.existsByPropriedadeRuralId(prop.getId());
                    boolean temAvulsos = avulsoRepository.existsByPropriedadeRuralId(prop.getId());

                    if (temNotas || temAvulsos) {
                        return ResponseEntity.badRequest().body("Não é possível excluir a propriedade '" + prop.getNome() + "'. Existem notas fiscais ou lançamentos do Livro Caixa vinculados a ela. Por favor, apague as notas atreladas a ela antes de excluir o imóvel.");
                    }
                }

                // 3. Se passou pela validação, remove do banco em segurança
                propriedadesAtuais.removeAll(propriedadesParaRemover);

                // Atualiza ou adiciona as propriedades
                for (PropriedadeRural pNovo : listaNovasPropriedades) {
                    if (pNovo.getId() != null) {
                        propriedadesAtuais.stream()
                                .filter(p -> p.getId().equals(pNovo.getId()))
                                .findFirst()
                                .ifPresent(pExistente -> {
                                    pExistente.setNome(pNovo.getNome());
                                    pExistente.setCpfCnpj(pNovo.getCpfCnpj());
                                    pExistente.setInscricaoEstadual(pNovo.getInscricaoEstadual());
                                    pExistente.setCaepf(pNovo.getCaepf());
                                    pExistente.setPercentualParticipacao(pNovo.getPercentualParticipacao());
                                });
                    } else {
                        // CORREÇÃO CRUCIAL: Vincula o produtor na nova propriedade antes de adicionar à lista
                        pNovo.setProdutor(produtor);
                        propriedadesAtuais.add(pNovo);
                    }
                }
            } else {
                produtor = new Produtor();
                produtor.setNome(nome);
                produtor.setCpfCnpj(cpfCnpj);
                produtor.setCnpj(cnpj);
                produtor.setTelefone(telefone);
                produtor.setEndereco(endereco);
                produtor.setContador(contadorOpt.get());

                for (PropriedadeRural p : listaNovasPropriedades) {
                    p.setProdutor(produtor);
                    produtor.getPropriedades().add(p);
                }
            }

            if (certificado != null && !certificado.isEmpty()) {
                byte[] bytesCertificado = certificado.getBytes();
                produtor.setCertificadoPfx(bytesCertificado);
                if (senhaCertificado != null && !senhaCertificado.isEmpty()) {
                    produtor.setSenhaCertificado(senhaCertificado);
                    try {
                        Date validade = certificadoService.extrairValidade(bytesCertificado, senhaCertificado);
                        produtor.setValidadeCertificado(validade);
                    } catch (Exception e) {
                        return ResponseEntity.badRequest().body("Senha do certificado incorreta ou arquivo inválido.");
                    }
                }
            }

            Produtor salvo = produtorRepository.save(produtor);
            return ResponseEntity.ok(salvo);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Erro interno ao salvar produtor: " + e.getMessage());
        }
    }

    @PutMapping("/desvincular/{id}")
    @Transactional
    public ResponseEntity<?> desvincularProdutor(@PathVariable Long id) {
        Optional<Produtor> produtorOpt = produtorRepository.findById(id);
        if (produtorOpt.isPresent()) {
            Produtor produtor = produtorOpt.get();
            produtor.setContador(null);
            produtorRepository.save(produtor);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

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

    @PostMapping("/login-mobile")
    @Transactional(readOnly = true)
    public ResponseEntity<?> loginMobile(@RequestBody Map<String, String> dados) {
        String cpfCnpj = dados.get("cpfCnpj");
        String cpfLimpo = cpfCnpj.replaceAll("[^0-9]", "");

        Optional<Produtor> produtorOpt = produtorRepository.findFirstByCpfCnpj(cpfLimpo);

        if (produtorOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Produtor não encontrado com este documento.");
        }

        Produtor produtor = produtorOpt.get();
        // Delegação da criação do token para o serviço seguro
        String token = tokenService.gerarToken(produtor);

        Map<String, Object> resposta = new HashMap<>();
        resposta.put("token", token);
        resposta.put("produtor", produtor);

        return ResponseEntity.ok(resposta);
    }
}