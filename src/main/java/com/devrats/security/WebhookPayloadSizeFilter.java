package com.devrats.security;

import com.devrats.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class WebhookPayloadSizeFilter extends OncePerRequestFilter {

    private final AppProperties properties;
    private final SecurityErrorWriter errorWriter;

    public WebhookPayloadSizeFilter(AppProperties properties, SecurityErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().equals("/api/v1/webhooks/github")
                && request.getContentLengthLong() > properties.getWebhook().getMaxPayloadBytes()) {
            //---- Reject before Spring buffers the body into a byte[].
            errorWriter.write(response, 413, "Payload muito grande", "O webhook excede o limite permitido");
            return;
        }
        chain.doFilter(request, response);
    }
}
