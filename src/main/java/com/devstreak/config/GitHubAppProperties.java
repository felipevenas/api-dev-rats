package com.devstreak.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.github")
@Getter
@Setter
public class GitHubAppProperties {

    private String apiUrl = "https://api.github.com";
    private String oauthUrl = "https://github.com/login/oauth";
    private String appId;
    private String clientId;
    private String clientSecret;
    private String appSlug;
    private String privateKey;
    private String webhookSecret;
    private String callbackUrl;
    private String setupUrl;

    public boolean isConfigured() {
        return appId != null && !appId.isBlank()
                && clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && privateKey != null && !privateKey.isBlank()
                && webhookSecret != null && !webhookSecret.isBlank();
    }
}
