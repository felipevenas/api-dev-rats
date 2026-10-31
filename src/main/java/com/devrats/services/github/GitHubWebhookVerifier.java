package com.devrats.services.github;

import com.devrats.config.GitHubAppProperties;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import com.devrats.utils.SignatureHelper;
import org.springframework.stereotype.Component;

@Component
public class GitHubWebhookVerifier {

    private final GitHubAppProperties properties;

    public GitHubWebhookVerifier(GitHubAppProperties properties) {
        this.properties = properties;
    }

    public void verify(byte[] payload, String signatureHeader) {
        //---- Fail closed: an empty secret must never validate a forged webhook.
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()
                || !SignatureHelper.isValidSha256(payload, properties.getWebhookSecret(), signatureHeader)) {
            throw new DomainException(ErrorCode.GITHUB_INVALID_SIGNATURE);
        }
    }
}
