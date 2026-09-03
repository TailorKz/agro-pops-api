package br.com.agropops.api.security;

import br.com.agropops.api.model.Admin;
import br.com.agropops.api.model.Contador;
import br.com.agropops.api.model.Produtor;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class TokenService {

    // Em produção, isso deve vir do application.properties / Variável de Ambiente
    @Value("${api.security.token.secret}")
    private String segredo;

    private static final String ISSUER = "AgroPops API";

    public String gerarToken(Contador contador) {
        return criarToken(contador.getEmail(), "id", contador.getId(), "CONTADOR", 30, ChronoUnit.DAYS);
    }

    public String gerarTokenImpersonate(Contador contador) {
        // Token com vida útil menor por segurança (1 dia)
        return criarToken(contador.getEmail(), "id", contador.getId(), "CONTADOR", 1, ChronoUnit.DAYS);
    }

    public String gerarTokenDesktop(Contador contador) {
        // Token de longa duração para o Robô (.exe)
        // O bloqueio de pagamento não será pela expiração deste token
        return criarToken(contador.getEmail(), "origem", "DESKTOP", "CONTADOR", 365, ChronoUnit.DAYS);
    }

    public String gerarToken(Admin admin) {
        return criarToken(admin.getEmail(), "role", "ADMIN", "ADMIN", 1, ChronoUnit.DAYS);
    }

    public String gerarToken(Produtor produtor) {
        return criarToken(produtor.getCpfCnpj(), "role", "PRODUTOR", "PRODUTOR", 30, ChronoUnit.DAYS);
    }

    public String validarTokenAndGetSubject(String token) {
        try {
            Algorithm algoritmo = Algorithm.HMAC256(segredo);
            return JWT.require(algoritmo)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (Exception exception) {
            return null; // O filtro tratará o nulo barrando a requisição
        }
    }

    private String criarToken(String subject, String claimKey, Object claimValue, String role, long amountToAdd, ChronoUnit unit) {
        try {
            Algorithm algoritmo = Algorithm.HMAC256(segredo);
            var builder = JWT.create()
                    .withIssuer(ISSUER)
                    .withSubject(subject)
                    .withExpiresAt(Instant.now().plus(amountToAdd, unit));

            // Diferenciação flexível de claims
            if (claimValue instanceof Long) {
                builder.withClaim(claimKey, (Long) claimValue);
            } else if (claimValue instanceof String) {
                builder.withClaim(claimKey, (String) claimValue);
            }

            return builder.sign(algoritmo);
        } catch (Exception exception) {
            throw new RuntimeException("Erro fatal ao gerar o token JWT", exception);
        }
    }
}