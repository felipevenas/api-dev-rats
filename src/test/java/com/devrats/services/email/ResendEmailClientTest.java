package com.devrats.services.email;

import com.devrats.config.EmailProperties;
import com.devrats.exceptions.EmailDeliveryException;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendEmailClientTest {

    private WireMockServer server;
    private EmailProperties properties;
    private ResendEmailClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();

        properties = new EmailProperties();
        properties.setMode(EmailProperties.EmailMode.LIVE);
        properties.setApiKey("re_test");
        properties.setBaseUrl("http://localhost:" + server.port());
        properties.setFrom("DevRats <nao-responda@eufelipedesign.cloud>");
        properties.setTimeout(Duration.ofSeconds(2));
        client = new ResendEmailClient(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void shouldSendEmailWithAuthIdempotencyAndTags() {
        server.stubFor(post(urlEqualTo("/emails"))
                .willReturn(okJson("{\"id\":\"email-123\"}")));

        EmailSendResult result = client.send(message());

        assertThat(result.id()).isEqualTo("email-123");
        server.verify(postRequestedFor(urlEqualTo("/emails"))
                .withHeader("Authorization", equalTo("Bearer re_test"))
                .withHeader("Idempotency-Key", equalTo("password-reset-token-id"))
                .withRequestBody(equalToJson("""
                        {
                          "from": "DevRats <nao-responda@eufelipedesign.cloud>",
                          "to": ["user@example.com"],
                          "subject": "Redefina sua senha no DevRats",
                          "html": "<p>link</p>",
                          "text": "link",
                          "reply_to": "qa@example.com",
                          "tags": [{"name": "type", "value": "password_reset"}]
                        }
                        """)));
    }

    @Test
    void shouldClassifyTooManyRequestsAsRetryable() {
        server.stubFor(post(urlEqualTo("/emails"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"rate_limit_exceeded\",\"message\":\"Too many requests\"}")));

        assertThatThrownBy(() -> client.send(message()))
                .isInstanceOfSatisfying(EmailDeliveryException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("rate_limit_exceeded");
                    assertThat(exception.getStatus()).isEqualTo(429);
                    assertThat(exception.isRetryable()).isTrue();
                });
    }

    @Test
    void shouldClassifyValidationErrorAsPermanent() {
        server.stubFor(post(urlEqualTo("/emails"))
                .willReturn(aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"name\":\"validation_error\",\"message\":\"Invalid from\"}")));

        assertThatThrownBy(() -> client.send(message()))
                .isInstanceOfSatisfying(EmailDeliveryException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("validation_error");
                    assertThat(exception.getStatus()).isEqualTo(422);
                    assertThat(exception.isRetryable()).isFalse();
                });
    }

    @Test
    void shouldReject_whenApiKeyIsMissing() {
        properties.setApiKey("");

        assertThatThrownBy(() -> client.send(message()))
                .isInstanceOfSatisfying(EmailDeliveryException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("EMAIL_NOT_CONFIGURED"));
    }

    private EmailMessage message() {
        return new EmailMessage(
                properties.getFrom(),
                "user@example.com",
                "Redefina sua senha no DevRats",
                "<p>link</p>",
                "link",
                "qa@example.com",
                "password-reset-token-id",
                List.of(new EmailTag("type", "password_reset")));
    }
}
