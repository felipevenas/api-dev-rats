package com.devrats.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.email")
@Getter
@Setter
public class EmailProperties {

    private EmailMode mode = EmailMode.OFF;
    private String apiKey;
    private String baseUrl = "https://api.resend.com";
    private String from = "DevRats <onboarding@resend.dev>";
    private String replyTo;
    private String testRecipient;
    private Duration timeout = Duration.ofSeconds(10);

    public boolean isConfigured() {
        if (mode == null || mode == EmailMode.OFF) {
            return false;
        }
        if (isBlank(apiKey) || isBlank(from)) {
            return false;
        }
        return mode != EmailMode.TEST || !isBlank(testRecipient);
    }

    public boolean isSandboxSender() {
        return from != null && from.toLowerCase().contains("resend.dev");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum EmailMode {
        OFF, TEST, LIVE
    }
}
