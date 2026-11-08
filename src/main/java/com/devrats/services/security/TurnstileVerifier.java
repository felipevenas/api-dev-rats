package com.devrats.services.security;

import com.devrats.config.AppProperties;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Valida o token do Cloudflare Turnstile no servidor.
 *
 * <p>O token é de uso único e vem no header {@code X-Turnstile-Token}. O segredo
 * nunca chega ao browser. Em indisponibilidade da Cloudflare, produção falha
 * fechado por padrão; {@code fail-open} existe apenas para ambientes locais.</p>
 */
@Component
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

    private final AppProperties.Turnstile properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public TurnstileVerifier(AppProperties appProperties, ObjectMapper objectMapper) {
        this.properties = appProperties.getTurnstile();
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    public void verify(String token, String remoteIp) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!properties.isConfigured()) {
            log.warn("Turnstile habilitado sem CLOUDFLARE_TURNSTILE_SECRET_KEY configurada");
            handleFailure(new IllegalStateException("Turnstile sem secret key"));
            return;
        }
        if (token == null || token.isBlank()) {
            throw new DomainException(ErrorCode.TURNSTILE_REQUIRED,
                    "Confirme que você não é um robô");
        }
        try {
            StringBuilder form = new StringBuilder()
                    .append("secret=").append(encode(properties.getSecretKey()))
                    .append("&response=").append(encode(token));
            if (remoteIp != null && !remoteIp.isBlank()) {
                form.append("&remoteip=").append(encode(remoteIp));
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getVerifyUrl()))
                    .timeout(properties.getTimeout())
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Turnstile respondeu status inesperado: {}", response.statusCode());
                handleFailure(new IllegalStateException("status " + response.statusCode()));
                return;
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.path("success").asBoolean()) {
                log.warn("Turnstile rejeitou o token: errors={}", root.path("error-codes"));
                throw new DomainException(ErrorCode.TURNSTILE_FAILED,
                        "Confirmação antirobô inválida");
            }
        } catch (DomainException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            handleFailure(exception);
        } catch (Exception exception) {
            handleFailure(exception);
        }
    }

    private void handleFailure(Exception exception) {
        log.warn("Falha ao verificar Turnstile: {}", exception.getMessage());
        if (properties.isFailOpen()) {
            return;
        }
        throw new DomainException(ErrorCode.TURNSTILE_UNAVAILABLE,
                "Não foi possível validar a confirmação antirobô");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
