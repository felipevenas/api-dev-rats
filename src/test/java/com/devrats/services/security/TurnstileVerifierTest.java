package com.devrats.services.security;

import com.devrats.config.AppProperties;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnstileVerifierTest {

    private WireMockServer server;
    private AppProperties properties;
    private TurnstileVerifier verifier;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        properties = new AppProperties();
        AppProperties.Turnstile turnstile = properties.getTurnstile();
        turnstile.setEnabled(true);
        turnstile.setSecretKey("test-secret");
        turnstile.setVerifyUrl("http://localhost:" + server.port() + "/siteverify");
        turnstile.setTimeout(Duration.ofSeconds(2));
        verifier = new TurnstileVerifier(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void shouldSkipVerification_whenDisabled() {
        properties.getTurnstile().setEnabled(false);

        assertThatCode(() -> verifier.verify(null, "127.0.0.1")).doesNotThrowAnyException();
        server.verify(0, postRequestedFor(urlEqualTo("/siteverify")));
    }

    @Test
    void shouldRequireToken_whenEnabled() {
        assertThatThrownBy(() -> verifier.verify(" ", "127.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).getErrorCode())
                        .isEqualTo(ErrorCode.TURNSTILE_REQUIRED));
    }

    @Test
    void shouldAcceptValidToken_andSendSecretAndResponse() {
        server.stubFor(post(urlEqualTo("/siteverify")).willReturn(okJson("{\"success\":true}")));

        assertThatCode(() -> verifier.verify("turnstile-token", "127.0.0.1")).doesNotThrowAnyException();

        server.verify(postRequestedFor(urlEqualTo("/siteverify"))
                .withRequestBody(containing("secret=test-secret"))
                .withRequestBody(containing("response=turnstile-token")));
    }

    @Test
    void shouldRejectInvalidToken() {
        server.stubFor(post(urlEqualTo("/siteverify"))
                .willReturn(okJson("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}")));

        assertThatThrownBy(() -> verifier.verify("bad-token", "127.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).getErrorCode())
                        .isEqualTo(ErrorCode.TURNSTILE_FAILED));
    }

    @Test
    void shouldFailClosed_whenCloudflareIsUnavailable() {
        server.stubFor(post(urlEqualTo("/siteverify")).willReturn(serverError()));

        assertThatThrownBy(() -> verifier.verify("token", "127.0.0.1"))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).getErrorCode())
                        .isEqualTo(ErrorCode.TURNSTILE_UNAVAILABLE));
    }

    @Test
    void shouldFailOpen_whenConfiguredForLocalDevelopment() {
        properties.getTurnstile().setFailOpen(true);
        server.stubFor(post(urlEqualTo("/siteverify")).willReturn(serverError()));

        assertThatCode(() -> verifier.verify("token", "127.0.0.1")).doesNotThrowAnyException();
    }
}
