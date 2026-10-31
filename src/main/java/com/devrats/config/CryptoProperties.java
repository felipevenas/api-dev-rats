package com.devrats.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.crypto")
@Validated
@Getter
@Setter
public class CryptoProperties {

    @NotBlank
    private String key;

    private int keyId = 1;
}
