package com.devrats.services.github;

import com.devrats.config.GitHubAppProperties;
import com.devrats.dto.github.GitHubInstallationTokenResponse;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubAppTokenProviderTest {

    @Test
    void shouldSignAppJwt_whenPrivateKeyIsPem() throws Exception {
        String pem = generatePem();
        assertToken(pem);
    }

    @Test
    void shouldSignAppJwt_whenPrivateKeyIsBase64OfPem() throws Exception {
        String base64 = Base64.getEncoder().encodeToString(generatePem().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertToken(base64);
    }

    private void assertToken(String privateKey) {
        GitHubAppProperties properties = new GitHubAppProperties();
        properties.setAppId("123456");
        properties.setClientId("client");
        properties.setClientSecret("secret");
        properties.setWebhookSecret("webhook");
        properties.setPrivateKey(privateKey);

        GitHubApiClient apiClient = mock(GitHubApiClient.class);
        when(apiClient.createInstallationToken(anyString(), eq(77L)))
                .thenReturn(new GitHubInstallationTokenResponse("installation-token", Instant.now().plusSeconds(3600)));

        GitHubAppTokenProvider provider = new GitHubAppTokenProvider(properties, apiClient,
                Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC));

        assertThat(provider.getInstallationToken(77L)).isEqualTo("installation-token");
        verify(apiClient).createInstallationToken(anyString(), eq(77L));
    }

    private String generatePem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        StringWriter writer = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(pair.getPrivate());
        }
        return writer.toString();
    }
}
