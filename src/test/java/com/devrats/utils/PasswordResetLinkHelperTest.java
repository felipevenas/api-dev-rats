package com.devrats.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetLinkHelperTest {

    @Test
    void shouldBuildFragmentLinkAndIgnoreTrailingSlash() {
        String link = PasswordResetLinkHelper.build("https://devrats.app/", "abc123");

        assertThat(link).isEqualTo("https://devrats.app/reset-password#token=abc123");
    }

    @Test
    void shouldKeepTokenOutOfTheQueryString() {
        String link = PasswordResetLinkHelper.build("http://localhost:5174", "token_value");

        assertThat(link).contains("#token=token_value");
        assertThat(link).doesNotContain("?token=");
    }
}
