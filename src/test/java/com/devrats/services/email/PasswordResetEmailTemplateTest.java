package com.devrats.services.email;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetEmailTemplateTest {

    private final PasswordResetEmailTemplate template = new PasswordResetEmailTemplate();

    @Test
    void shouldEscapeDisplayNameAndIncludeResetUrl() {
        EmailContent content = template.build(email("<script>alert(1)</script>"), false, null);

        assertThat(content.subject()).isEqualTo("Redefina sua senha no DevRats");
        assertThat(content.html()).doesNotContain("<script>");
        assertThat(content.html()).contains("&lt;script&gt;");
        assertThat(content.html()).contains("https://devrats.app/reset-password#token=abc123");
        assertThat(content.text()).contains("https://devrats.app/reset-password#token=abc123");
        assertThat(content.text()).contains("30 minutos");
    }

    @Test
    void shouldAddTestBannerAndSubject_whenTestMode() {
        EmailContent content = template.build(email("Felipe"), true, "qa@example.com");

        assertThat(content.subject()).startsWith("[TESTE]");
        assertThat(content.html()).contains("MODO DE TESTE");
        assertThat(content.html()).contains("qa@example.com");
    }

    @Test
    void shouldFallbackToDefaultName_whenDisplayNameIsBlank() {
        EmailContent content = template.build(email(" "), false, null);

        assertThat(content.html()).contains("Olá, dev.");
    }

    private PasswordResetEmail email(String displayName) {
        return new PasswordResetEmail(
                UUID.randomUUID(),
                "user@example.com",
                displayName,
                "https://devrats.app/reset-password#token=abc123",
                30,
                "password-reset-token-id");
    }
}
