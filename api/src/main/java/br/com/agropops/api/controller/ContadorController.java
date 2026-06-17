package br.com.agropops.api.controller;

import br.com.agropops.api.model.Contador;
import br.com.agropops.api.model.LoginDTO;
import br.com.agropops.api.repository.ContadorRepository;
import br.com.agropops.api.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/contadores")
@CrossOrigin(origins = "*")
public class ContadorController {

    @Autowired
    private ContadorRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenService tokenService;

    @PostMapping("/registrar")
    public ResponseEntity<?> registrarContador(@RequestBody Contador novoContador) {
        if (repository.findByEmail(novoContador.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Erro: Este e-mail já está registado.");
        }

        // Transforma a palavra-passe em código BCrypt antes de guardar
        novoContador.setSenha(passwordEncoder.encode(novoContador.getSenha()));

        Contador contadorSalvo = repository.save(novoContador);
        return ResponseEntity.ok(contadorSalvo);
    }

    @PostMapping("/login")
    public ResponseEntity<?> fazerLogin(@RequestBody LoginDTO loginData) {
        Optional<Contador> contadorOpt = repository.findByEmail(loginData.getEmail());

        if (contadorOpt.isPresent()) {
            Contador contador = contadorOpt.get();

            // Compara a palavra-passe digitada no React com o código Hash da base de dados
            if (passwordEncoder.matches(loginData.getSenha(), contador.getSenha())) {

                // Gera o Crachá JWT
                String token = tokenService.gerarToken(contador);

                // Devolve o token e os dados do contador (empacotados num objeto JSON)
                Map<String, Object> resposta = new HashMap<>();
                resposta.put("token", token);
                resposta.put("contador", contador);

                return ResponseEntity.ok(resposta);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Palavra-passe incorreta.");
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("E-mail não encontrado no sistema.");
    }
}