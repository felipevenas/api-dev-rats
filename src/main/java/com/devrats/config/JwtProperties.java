package com.devrats.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
@Validated
@Getter
@Setter
public class JwtProperties {

    @NotBlank
    private String secret;

    @NotBlank
    private String issuer;

    @NotBlank
    private String audience;

    @NotNull
    private Duration accessTtl = Duration.ofMinutes(15);

    @NotNull
    private Duration refreshTtl = Duration.ofDays(30);
}
