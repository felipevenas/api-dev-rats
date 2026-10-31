package com.devrats.services.github;

import com.devrats.config.CacheConfig;
import com.devrats.config.GitHubAppProperties;
import com.devrats.dto.github.GitHubInstallationTokenResponse;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Service
public class GitHubAppTokenProvider {

    private final GitHubAppProperties properties;
    private final GitHubApiClient apiClient;
    private final Clock clock;

    public GitHubAppTokenProvider(GitHubAppProperties properties, GitHubApiClient apiClient, Clock clock) {
        this.properties = properties;
        this.apiClient = apiClient;
        this.clock = clock;
    }

    //---- Installation tokens last one hour and are never persisted; Caffeine expires them earlier.
    @Cacheable(cacheNames = CacheConfig.INSTALLATION_TOKENS, key = "#installationId")
    public String getInstallationToken(Long installationId) {
        if (!properties.isConfigured()) {
            throw new DomainException(ErrorCode.GITHUB_NOT_CONNECTED, "GitHub App não configurada");
        }
        GitHubInstallationTokenResponse response = apiClient.createInstallationToken(createAppJwt(), installationId);
        return response.token();
    }

    private String createAppJwt() {
        try {
            Instant now = clock.instant();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(properties.getAppId())
                    .issueTime(Date.from(now.minusSeconds(60)))
                    .expirationTime(Date.from(now.plusSeconds(540)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(readPrivateKey(properties.getPrivateKey())));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar JWT da GitHub App", e);
        }
    }

    private RSAPrivateKey readPrivateKey(String value) throws Exception {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("app.github.private-key não configurada");
        }
        String text = value.replace("\\n", "\n");
        if (text.contains("BEGIN")) {
            return parsePem(text);
        }
        byte[] decoded = Base64.getDecoder().decode(text.replaceAll("\\s", ""));
        String decodedText = new String(decoded, StandardCharsets.UTF_8);
        //---- Accept both base64(PEM) and base64(PKCS#8 DER), which secret managers commonly store.
        if (decodedText.contains("BEGIN")) {
            return parsePem(decodedText);
        }
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private RSAPrivateKey parsePem(String pem) throws Exception {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object object = parser.readObject();
            PrivateKeyInfo privateKeyInfo = object instanceof PEMKeyPair keyPair
                    ? keyPair.getPrivateKeyInfo()
                    : (PrivateKeyInfo) object;
            return (RSAPrivateKey) new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo);
        }
    }
}
