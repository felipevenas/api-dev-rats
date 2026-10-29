package com.devstreak.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureHelperTest {

    private final byte[] payload = "{\"ref\":\"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8);
    private final String secret = "dev-webhook-secret";

    @Test
    void shouldAcceptSignature_whenPayloadAndSecretMatch() {
        String signature = "sha256=" + HexFormat.of().formatHex(SignatureHelper.hmacSha256(payload, secret));

        assertThat(SignatureHelper.isValidSha256(payload, secret, signature)).isTrue();
    }

    @Test
    void shouldRejectSignature_whenPayloadChanges() {
        String signature = "sha256=" + HexFormat.of().formatHex(SignatureHelper.hmacSha256(payload, secret));

        assertThat(SignatureHelper.isValidSha256("other".getBytes(StandardCharsets.UTF_8), secret, signature)).isFalse();
    }

    @Test
    void shouldRejectSignature_whenHeaderIsMissingOrMalformed() {
        assertThat(SignatureHelper.isValidSha256(payload, secret, null)).isFalse();
        assertThat(SignatureHelper.isValidSha256(payload, secret, "sha1=abc")).isFalse();
        assertThat(SignatureHelper.isValidSha256(payload, secret, "sha256=not-hex")).isFalse();
    }
}
