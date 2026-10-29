package com.devstreak.services.github;

import com.devstreak.config.GitHubAppProperties;
import com.devstreak.exceptions.DomainException;
import com.devstreak.exceptions.ErrorCode;
import com.devstreak.utils.SignatureHelper;
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
