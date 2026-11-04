package com.devrats.services.impl;

import com.devrats.config.AppProperties;
import com.devrats.exceptions.RateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocialRateLimitServiceImplTest {

    private static final UUID FIRST_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private SocialRateLimitServiceImpl service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getSocial().setSearchPerMinute(2);
        properties.getSocial().setFriendRequestPerHour(3);
        service = new SocialRateLimitServiceImpl(properties);
    }

    @Test
    void shouldBlockSearchAfterPerUserLimit() {
        service.checkSearch(FIRST_USER);
        service.checkSearch(FIRST_USER);

        assertThatThrownBy(() -> service.checkSearch(FIRST_USER))
                .isInstanceOf(RateLimitException.class)
                .satisfies(exception -> assertThat(((RateLimitException) exception).getRetryAfter().toSeconds())
                        .isPositive());
        //---- Outro usuário tem o próprio balde.
        service.checkSearch(SECOND_USER);
    }

    @Test
    void shouldBlockFriendRequestsAfterPerUserLimit() {
        service.checkFriendRequest(FIRST_USER);
        service.checkFriendRequest(FIRST_USER);
        service.checkFriendRequest(FIRST_USER);

        assertThatThrownBy(() -> service.checkFriendRequest(FIRST_USER))
                .isInstanceOf(RateLimitException.class);
        service.checkFriendRequest(SECOND_USER);
    }
}
