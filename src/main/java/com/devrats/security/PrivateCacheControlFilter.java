package com.devrats.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Marca respostas privadas do grafo social como {@code Cache-Control: no-store}.
 *
 * <p>Cobre {@code /api/v1/me}, {@code /api/v1/users}, {@code /api/v1/friends} e
 * {@code /api/v1/presence} — inclusive respostas de erro — para que proxies e o
 * browser não guardem perfil, export LGPD, presença ou solicitações.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class PrivateCacheControlFilter extends OncePerRequestFilter {

    private static final List<String> PRIVATE_PREFIXES = List.of(
            "/api/v1/me",
            "/api/v1/users",
            "/api/v1/friends",
            "/api/v1/presence"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isPrivatePath(normalizePath(request.getRequestURI()))) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        chain.doFilter(request, response);
    }

    private boolean isPrivatePath(String path) {
        return PRIVATE_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    //---- Spring MVC ignora parâmetros de matriz; removê-los evita bypass do filtro.
    private String normalizePath(String path) {
        return path.replaceAll(";[^/]*", "");
    }
}
