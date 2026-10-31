package com.devrats.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String INSTALLATION_TOKENS = "installationTokens";
    public static final String OAUTH_STATES = "oauthStates";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(INSTALLATION_TOKENS, OAUTH_STATES);
        //---- Both entries are short-lived: installation tokens are refreshed by the API and OAuth states are one-shot.
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .recordStats());
        return manager;
    }
}
