package br.com.agropops.api.controller;

import br.com.agropops.api.model.Admin;
import br.com.agropops.api.model.Contador;
import br.com.agropops.api.model.Produtor;
import br.com.agropops.api.repository.AdminRepository;
import br.com.agropops.api.repository.ContadorRepository;
import br.com.agropops.api.repository.ProdutorRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admins")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired private AdminRepository adminRepository;
    @Autowired private ContadorRepository contadorRepository;
    @Autowired private ProdutorRepository produtorRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private final String segredo = "MinhaChaveSuperSecretaDoAgroContabil";

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> dados) {
        String email = dados.get("email");
        String senha = dados.get("senha");
        Optional<Admin> adminOpt = adminRepository.findByEmail(email);

        if (adminOpt.isPresent() && passwordEncoder.matches(senha, adminOpt.get().getSenha())) {
            Admin admin = adminOpt.get();
            String token = JWT.create()
                    .withIssuer("AgroPops API")
                    .withSubject(admin.getEmail())
                    .withClaim("role", "ADMIN")
                    .withExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                    .sign(Algorithm.HMAC256(segredo));

            Map<String, Object> resp = new HashMap<>();
            resp.put("token", token);
            resp.put("admin", admin);
            return ResponseEntity.ok(resp);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciais inválidas.");
    }

    @PostMapping("/novo-admin")
    public ResponseEntity<?> criarAdmin(@RequestBody Admin novoAdmin) {
        if (adminRepository.findByEmail(novoAdmin.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("E-mail já está em uso.");
        }
        novoAdmin.setSenha(passwordEncoder.encode(novoAdmin.getSenha()));
        return ResponseEntity.ok(adminRepository.save(novoAdmin));
    }

    @PutMapping("/perfil/{id}")
    public ResponseEntity<?> atualizarPerfil(@PathVariable Long id, @RequestBody Admin dados) {
        return adminRepository.findById(id).map(admin -> {
            admin.setNome(dados.getNome());
            admin.setEmail(dados.getEmail());
            return ResponseEntity.ok(adminRepository.save(admin));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/senha/{id}")
    public ResponseEntity<?> atualizarSenha(@PathVariable Long id, @RequestBody Map<String, String> dados) {
        return adminRepository.findById(id).map(admin -> {
            if (!passwordEncoder.matches(dados.get("senhaAtual"), admin.getSenha())) {
                return ResponseEntity.badRequest().body("Senha atual incorreta.");
            }
            admin.setSenha(passwordEncoder.encode(dados.get("novaSenha")));
            adminRepository.save(admin);
            return ResponseEntity.ok("Senha atualizada com sucesso.");
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/contadores")
    public ResponseEntity<List<Contador>> listarContadores() {
        return ResponseEntity.ok(contadorRepository.findAll());
    }

    @PostMapping("/impersonate")
    public ResponseEntity<?> impersonate(@RequestParam String tipoUsuario, @RequestParam Long usuarioId) {
        if ("CONTADOR".equalsIgnoreCase(tipoUsuario)) {
            Optional<Contador> contadorOpt = contadorRepository.findById(usuarioId);
            if (contadorOpt.isPresent()) {
                Contador contador = contadorOpt.get();
                String token = JWT.create()
                        .withIssuer("AgroPops API")
                        .withSubject(contador.getEmail())
                        .withClaim("id", contador.getId())
                        .withExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                        .sign(Algorithm.HMAC256(segredo));

                Map<String, Object> resp = new HashMap<>();
                resp.put("token", token);
                resp.put("user", contador);
                return ResponseEntity.ok(resp);
            }
        }
        return ResponseEntity.badRequest().body("Usuário não encontrado.");
    }

    @PutMapping("/reset-senha")
    public ResponseEntity<?> resetSenha(@RequestParam String tipoUsuario, @RequestParam Long usuarioId, @RequestParam String novaSenha) {
        if ("CONTADOR".equalsIgnoreCase(tipoUsuario)) {
            return contadorRepository.findById(usuarioId).map(c -> {
                c.setSenha(passwordEncoder.encode(novaSenha));
                contadorRepository.save(c);
                return ResponseEntity.ok().build();
            }).orElse(ResponseEntity.notFound().build());
        }
        // Fica pendente o do produtor até resolvermos o campo de senha no banco de dados
        return ResponseEntity.badRequest().build();
    }

    @PutMapping("/transferir-produtor/{produtorId}/{targetContadorId}")
    public ResponseEntity<?> transferirProdutor(@PathVariable Long produtorId, @PathVariable Long targetContadorId) {
        Optional<Produtor> produtorOpt = produtorRepository.findById(produtorId);
        Optional<Contador> contadorOpt = contadorRepository.findById(targetContadorId);

        if (produtorOpt.isPresent() && contadorOpt.isPresent()) {
            Produtor produtor = produtorOpt.get();
            produtor.setContador(contadorOpt.get());
            produtorRepository.save(produtor);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/deletar-produtor/{produtorId}")
    @Transactional
    public ResponseEntity<?> deletarProdutor(@PathVariable Long produtorId) {
        if (produtorRepository.existsById(produtorId)) {
            produtorRepository.deleteById(produtorId);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/metricas")
    public ResponseEntity<?> getMetricas() {
        long contadores = contadorRepository.count();
        long produtores = produtorRepository.count();

        Map<String, Object> metricas = new HashMap<>();
        metricas.put("contadoresAtivos", contadores);
        metricas.put("produtoresAtivos", produtores);
        metricas.put("statusSistema", "Online e Estável");

        return ResponseEntity.ok(metricas);
    }

    @GetMapping("/produtores")
    public ResponseEntity<List<Map<String, Object>>> listarTodosProdutores() {
        return ResponseEntity.ok(produtorRepository.findAll().stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("nome", p.getNome());
            map.put("cpfCnpj", p.getCpfCnpj());
            map.put("cnpj", p.getCnpj());
            map.put("contadorId", p.getContador() != null ? p.getContador().getId() : null);
            map.put("contadorNome", p.getContador() != null ? p.getContador().getNomeEscritorio() : "Sem Vínculo (Independente)");
            return map;
        }).collect(java.util.stream.Collectors.toList()));
    }
}