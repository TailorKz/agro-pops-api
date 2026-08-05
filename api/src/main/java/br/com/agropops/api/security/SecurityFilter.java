package br.com.agropops.api.security;

import br.com.agropops.api.model.Admin;
import br.com.agropops.api.model.Contador;
import br.com.agropops.api.model.Produtor;
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
    private UsuarioAuthService authService;

    @Autowired
    private TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var token = this.recoverToken(request);
        if (token != null) {
            String subject = tokenService.validarTokenAndGetSubject(token);

            if (subject != null) {
                System.out.println("  [Filtro JWT] Crachá validado para: " + subject);

                if (subject.contains("@")) {
                    // 1. Tenta buscar como Admin primeiro
                    Admin admin = authService.buscarAdmin(subject);
                    if (admin != null) {
                        autenticarNoContexto(admin, request);
                        System.out.println("  Acesso Liberado para ADMIN: " + admin.getNome());
                    } else {
                        // 2. Se não for Admin, tenta como Contador
                        Contador contador = authService.buscarContador(subject);
                        if (contador != null) {
                            autenticarNoContexto(contador, request);
                            System.out.println("  Acesso Liberado para Contador: " + contador.getNomeEscritorio());
                        }
                    }
                } else {
                    // 3. Produtor (Mobile)
                    Produtor produtor = authService.buscarProdutor(subject);
                    if (produtor != null) {
                        autenticarNoContexto(produtor, request);
                        System.out.println("  Acesso Liberado para Produtor: " + produtor.getNome());
                    } else {
                        System.out.println("  [Aviso] Produtor não encontrado com CPF: " + subject);
                    }
                }
            } else {
                System.out.println("  [Aviso] Token inválido ou expirado interceptado.");
            }
        }
        filterChain.doFilter(request, response);
    }

    private void autenticarNoContexto(Object usuario, HttpServletRequest request) {
        var authentication = new UsernamePasswordAuthenticationToken(usuario, null, Collections.emptyList());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        return authHeader.replace("Bearer ", "");
    }
}