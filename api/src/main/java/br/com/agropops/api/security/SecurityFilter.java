package br.com.agropops.api.security;

import br.com.agropops.api.model.Contador;
import br.com.agropops.api.repository.ContadorRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private ContadorRepository contadorRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var token = this.recoverToken(request);
        if (token != null) {
            try {
                // Usa a mesma chave secreta que foi definida no TokenService
                Algorithm algoritmo = Algorithm.HMAC256("MinhaChaveSuperSecretaDoAgroContabil");
                String email = JWT.require(algoritmo)
                        .withIssuer("AgroPops API")
                        .build()
                        .verify(token)
                        .getSubject();

                Contador contador = contadorRepository.findByEmail(email).orElse(null);
                if (contador != null) {
                    var authentication = new UsernamePasswordAuthenticationToken(contador, null, Collections.emptyList());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    // Libera o utilizador
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                System.out.println("Erro ao validar token JWT: " + e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null) return null;
        return authHeader.replace("Bearer ", "");
    }
}