package com.devrats.services.email;

import com.devrats.config.EmailProperties;
import com.devrats.exceptions.EmailDeliveryException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResendEmailClient {

    private static final String DEFAULT_BASE_URL = "https://api.resend.com";

    private final RestClient restClient;
    private final EmailProperties properties;
    private final ObjectMapper objectMapper;

    public ResendEmailClient(EmailProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(properties.getTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public EmailSendResult send(EmailMessage message) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new EmailDeliveryException("EMAIL_NOT_CONFIGURED", "Chave do Resend não configurada", null, false);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", message.from());
        body.put("to", List.of(message.to()));
        body.put("subject", message.subject());
        body.put("html", message.html());
        body.put("text", message.text());
        if (message.replyTo() != null && !message.replyTo().isBlank()) {
            body.put("reply_to", message.replyTo());
        }
        if (!message.tags().isEmpty()) {
            body.put("tags", message.tags().stream()
                    .map(tag -> Map.of("name", tag.name(), "value", tag.value()))
                    .toList());
        }

        try {
            ResendEmailResponse response = restClient.post()
                    .uri(baseUrl() + "/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header("Idempotency-Key", message.idempotencyKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ResendEmailResponse.class);
            if (response == null || response.id() == null || response.id().isBlank()) {
                throw new EmailDeliveryException("EMAIL_MISSING_ID", "Resend não retornou o ID do e-mail", null, true);
            }
            return new EmailSendResult(response.id());
        } catch (RestClientResponseException exception) {
            throw toDeliveryException(exception);
        } catch (ResourceAccessException exception) {
            throw new EmailDeliveryException("EMAIL_NETWORK", "Falha de rede ao enviar e-mail", null, true, exception);
        } catch (RestClientException exception) {
            throw new EmailDeliveryException("EMAIL_INVALID_RESPONSE", "Resposta inválida do Resend", null, false, exception);
        }
    }

    private String baseUrl() {
        String base = properties.getBaseUrl();
        if (base == null || base.isBlank()) {
            return DEFAULT_BASE_URL;
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private EmailDeliveryException toDeliveryException(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String code = "HTTP_" + status;
        String message = "Resend respondeu " + status;
        try {
            JsonNode payload = objectMapper.readTree(exception.getResponseBodyAsString());
            String name = payload.path("name").asText("");
            String detail = payload.path("message").asText("");
            if (!name.isBlank()) {
                code = name;
            }
            if (!detail.isBlank()) {
                message = detail;
            }
        } catch (Exception ignored) {
            //---- O corpo pode não ser JSON; o status HTTP ainda classifica o erro.
        }
        boolean retryable = status == 429 || status >= 500;
        return new EmailDeliveryException(code, message, status, retryable, exception);
    }

    private record ResendEmailResponse(String id) {
    }
}
