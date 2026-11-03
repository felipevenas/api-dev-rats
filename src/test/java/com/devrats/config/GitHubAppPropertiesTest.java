package com.devrats.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubAppPropertiesTest {

    @Test
    void shouldNotBeConfigured_whenSlugOrRedirectUrlsAreMissing() {
        GitHubAppProperties properties = completeProperties();
        properties.setAppSlug(" ");

        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void shouldBeConfigured_whenEveryRequiredValueIsPresent() {
        assertThat(completeProperties().isConfigured()).isTrue();
    }

    private GitHubAppProperties completeProperties() {
        GitHubAppProperties properties = new GitHubAppProperties();
        properties.setAppId("123");
        properties.setClientId("client");
        properties.setClientSecret("secret");
        properties.setPrivateKey("key");
        properties.setWebhookSecret("webhook");
        properties.setAppSlug("devstreak");
        properties.setCallbackUrl("http://localhost/callback");
        properties.setSetupUrl("http://localhost/setup");
        return properties;
    }
}
