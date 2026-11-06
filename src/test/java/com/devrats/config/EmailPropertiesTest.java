package com.devrats.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailPropertiesTest {

    @Test
    void shouldNotBeConfigured_whenModeIsOff() {
        EmailProperties properties = liveProperties();
        properties.setMode(EmailProperties.EmailMode.OFF);

        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void shouldNotBeConfigured_whenLiveHasNoApiKey() {
        EmailProperties properties = liveProperties();
        properties.setApiKey(" ");

        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void shouldBeConfigured_whenLiveHasKeyAndSender() {
        assertThat(liveProperties().isConfigured()).isTrue();
    }

    @Test
    void shouldNotBeConfigured_whenTestModeHasNoRecipient() {
        EmailProperties properties = liveProperties();
        properties.setMode(EmailProperties.EmailMode.TEST);
        properties.setTestRecipient("");

        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void shouldBeConfigured_whenTestModeHasRecipient() {
        EmailProperties properties = liveProperties();
        properties.setMode(EmailProperties.EmailMode.TEST);
        properties.setTestRecipient("qa@example.com");

        assertThat(properties.isConfigured()).isTrue();
    }

    @Test
    void shouldDetectSandboxSender() {
        EmailProperties properties = liveProperties();
        properties.setFrom("DevRats <onboarding@resend.dev>");

        assertThat(properties.isSandboxSender()).isTrue();
    }

    private EmailProperties liveProperties() {
        EmailProperties properties = new EmailProperties();
        properties.setMode(EmailProperties.EmailMode.LIVE);
        properties.setApiKey("re_test");
        properties.setFrom("DevRats <nao-responda@eufelipedesign.cloud>");
        return properties;
    }
}
