package br.com.agropops.api.security;

import br.com.agropops.api.model.Contador;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class TokenService {

    // TODO1 chave exemplo
    private final String segredo = "MinhaChaveSuperSecretaDoAgroContabil";

    public String gerarToken(Contador contador) {
        try {
            Algorithm algoritmo = Algorithm.HMAC256(segredo);
            return JWT.create()
                    .withIssuer("AgroPops API")
                    .withSubject(contador.getEmail())
                    .withClaim("id", contador.getId())
                    // O token expira em 2 horas (O contador terá de fazer login novamente depois)
                    .withExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                    .sign(algoritmo);
        } catch (Exception exception) {
            throw new RuntimeException("Erro ao gerar o token JWT", exception);
        }
    }
}