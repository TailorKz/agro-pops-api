package br.com.agropops.api.controller;

import br.com.agropops.api.model.Admin;
import br.com.agropops.api.model.Contador;
import br.com.agropops.api.model.Produtor;
import br.com.agropops.api.model.RegraGlobal;
import br.com.agropops.api.repository.AdminRepository;
import br.com.agropops.api.repository.ContadorRepository;
import br.com.agropops.api.repository.ProdutorRepository;
import br.com.agropops.api.repository.RegraGlobalRepository;
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
import java.util.ArrayList;
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

    @Autowired private RegraGlobalRepository regraGlobalRepository;

    private final String segredo = "MinhaChaveSuperSecretaDoAgroContabil";

    // Roda automaticamente quando o servidor liga

    @PostConstruct
    public void popularRegrasPadrao() {
        if (regraGlobalRepository.count() == 0) {
            System.out.println("  [AUTO-SEED] Tabela de Regras Globais vazia. Injetando matriz tributária padrão...");
            List<RegraGlobal> regras = new ArrayList<>();

            // GRUPO 1: CFOPs DEDUTÍVEIS
            regras.add(criarRegra("CFOP", "101", "Venda de produção própria ou de terceiros", true));
            regras.add(criarRegra("CFOP", "102", "Venda de produção própria ou de terceiros", true));
            regras.add(criarRegra("CFOP", "401", "Venda com substituição tributária (óleos, pneus)", true));
            regras.add(criarRegra("CFOP", "403", "Venda com substituição tributária", true));
            regras.add(criarRegra("CFOP", "405", "Venda com substituição tributária", true));
            regras.add(criarRegra("CFOP", "551", "Venda de bem do ativo imobilizado (Máquinas/Implementos)", true));
            regras.add(criarRegra("CFOP", "352", "Prestações de serviço de transporte (Frete de insumos)", true));
            regras.add(criarRegra("CFOP", "353", "Prestações de serviço de transporte (Frete de insumos)", true));
            regras.add(criarRegra("CFOP", "356", "Prestações de serviço de transporte (Frete de insumos)", true));
            regras.add(criarRegra("CFOP", "253", "Venda de energia elétrica vinculada à produção", true));
            regras.add(criarRegra("CFOP", "255", "Venda de energia elétrica vinculada à production", true));
            regras.add(criarRegra("CFOP", "257", "Venda de energia elétrica vinculada à produção", true));

            // GRUPO 2 e 3: CFOPs NÃO DEDUTÍVEIS OU ALERTA HUMANO (Salvando como false)
            regras.add(criarRegra("CFOP", "901", "Remessa para venda fora do estabelecimento", false));
            regras.add(criarRegra("CFOP", "904", "Remessa para depósito fechado ou armazém (Guarda de Grãos)", false));
            regras.add(criarRegra("CFOP", "910", "Remessa para demonstração ou feiras", false));
            regras.add(criarRegra("CFOP", "915", "Remessa para conserto ou manutenção", false));
            regras.add(criarRegra("CFOP", "201", "Devoluções de vendas ou compras", false));
            regras.add(criarRegra("CFOP", "202", "Devoluções de vendas ou compras", false));
            regras.add(criarRegra("CFOP", "908", "Remessa de bens por contrato de comodato", false));
            regras.add(criarRegra("CFOP", "556", "ALERTA: Venda de material de uso e consumo", false));
            regras.add(criarRegra("CFOP", "653", "ALERTA: Combustível. Diesel (Trator) = SIM. Gasolina (Passeio) = NÃO.", false));
            regras.add(criarRegra("CFOP", "922", "ALERTA: Simples faturamento (Faturamento Antecipado)", false));

            // NCMs (Capítulos Dedutíveis do Agro)
            regras.add(criarRegra("NCM", "01", "Animais vivos", true));
            regras.add(criarRegra("NCM", "10", "Cereais", true));
            regras.add(criarRegra("NCM", "12", "Sementes e frutos oleaginosos; forragens", true));
            regras.add(criarRegra("NCM", "31", "Adubos (fertilizantes)", true));
            regras.add(criarRegra("NCM", "38", "Produtos diversos das indústrias químicas (Defensivos)", true));
            regras.add(criarRegra("NCM", "84", "Tratores, caldeiras, aparelhos mecânicos", true));
            regras.add(criarRegra("NCM", "87", "Veículos automóveis, tratores, ciclos", true));

            // NCMs (Despesas Pessoais / Não Dedutíveis)
            regras.add(criarRegra("NCM", "02", "Carnes e miudezas (Uso Pessoal)", false));
            regras.add(criarRegra("NCM", "16", "Preparações de carne/peixes", false));
            regras.add(criarRegra("NCM", "22", "Bebidas, líquidos alcoólicos (Uso Pessoal)", false));
            regras.add(criarRegra("NCM", "24", "Tabaco e sucedâneos (Uso Pessoal)", false));
            regras.add(criarRegra("NCM", "33", "Cosméticos e perfumaria (Uso Pessoal)", false));
            regras.add(criarRegra("NCM", "61", "Vestuário e acessórios (Uso Pessoal)", false));
            regras.add(criarRegra("NCM", "64", "Calçados (Uso Pessoal)", false));

            regraGlobalRepository.saveAll(regras);
            System.out.println("  [AUTO-SEED] Sucesso! Regras injetadas no banco de dados.");
        }
    }

    private RegraGlobal criarRegra(String tipo, String codigo, String descricao, boolean isDedutivel) {
        RegraGlobal r = new RegraGlobal();
        r.setTipo(tipo);
        r.setCodigo(codigo);
        r.setDescricao(descricao);
        r.setIsDedutivel(isDedutivel);
        return r;
    }

    // ENDPOINTS DE REGRAS GLOBAIS

    @GetMapping("/regras-globais")
    public ResponseEntity<List<RegraGlobal>> listarRegrasGlobais() {
        return ResponseEntity.ok(regraGlobalRepository.findAll());
    }

    @PostMapping("/regras-globais")
    public ResponseEntity<RegraGlobal> criarRegraGlobal(@RequestBody RegraGlobal regra) {
        // Se a regra já existe (CFOP 101 por exemplo), não cria duplicado.
        Optional<RegraGlobal> existe = regraGlobalRepository.findByTipoAndCodigo(regra.getTipo(), regra.getCodigo());
        if (existe.isPresent()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(regraGlobalRepository.save(regra));
    }

    @DeleteMapping("/regras-globais/{id}")
    public ResponseEntity<?> deletarRegraGlobal(@PathVariable Long id) {
        if (regraGlobalRepository.existsById(id)) {
            regraGlobalRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ENDPOINTS ORIGINAIS DE LOGIN/ADMINS

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