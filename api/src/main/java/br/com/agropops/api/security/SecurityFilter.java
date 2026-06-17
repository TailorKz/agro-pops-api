package br.com.agropops.api.security;

import br.com.agropops.api.model.Contador;
import br.com.agropops.api.model.Produtor;
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
    private UsuarioAuthService authService; // <-- USANDO O NOVO SERVIÇO BLINDADO

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var token = this.recoverToken(request);
        if (token != null) {
            try {
                Algorithm algoritmo = Algorithm.HMAC256("MinhaChaveSuperSecretaDoAgroContabil");
                String subject = JWT.require(algoritmo)
                        .withIssuer("AgroPops API")
                        .build()
                        .verify(token)
                        .getSubject();

                System.out.println("🕵️ Fiscal encontrou um Crachá de: " + subject);

                if (subject.contains("@")) {
                    // Delega a busca para o serviço transacional
                    Contador contador = authService.buscarContador(subject);
                    if (contador != null) {
                        var authentication = new UsernamePasswordAuthenticationToken(contador, null, Collections.emptyList());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        System.out.println("✅ Acesso Liberado para Contador: " + contador.getNomeEscritorio());
                    }
                } else {
                    // Delega a busca para o serviço transacional (O PostgreSQL agora lê o @Lob!)
                    Produtor produtor = authService.buscarProdutor(subject);
                    if (produtor != null) {
                        var authentication = new UsernamePasswordAuthenticationToken(produtor, null, Collections.emptyList());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        System.out.println("✅ Acesso Liberado para Produtor: " + produtor.getNome());
                    } else {
                        System.out.println("❌ Produtor não encontrado no banco com CPF: " + subject);
                    }
                }
            } catch (Exception e) {
                System.out.println("❌ Erro ao validar token JWT: " + e.getMessage());
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