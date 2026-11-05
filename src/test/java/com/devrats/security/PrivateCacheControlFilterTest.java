package com.devrats.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateCacheControlFilterTest {

    private final PrivateCacheControlFilter filter = new PrivateCacheControlFilter();

    @Test
    void shouldMarkSocialAndAccountResponsesAsNoStore() throws Exception {
        assertThat(cacheControl("/api/v1/me/export")).isEqualTo("no-store");
        assertThat(cacheControl("/api/v1/users/ana")).isEqualTo("no-store");
        assertThat(cacheControl("/api/v1/friends")).isEqualTo("no-store");
        assertThat(cacheControl("/api/v1/presence/heartbeat")).isEqualTo("no-store");
        assertThat(cacheControl("/api/v1/chat/conversations")).isEqualTo("no-store");
        assertThat(cacheControl("/api/v1/chat/attachments/abc/url")).isEqualTo("no-store");
    }

    @Test
    void shouldIgnoreUnrelatedPathsAndNormalizeMatrixParams() throws Exception {
        assertThat(cacheControl("/api/v1/dashboard")).isNull();
        assertThat(cacheControl("/api/v1/usersX")).isNull();
        assertThat(cacheControl("/api/v1/users;v=2")).isEqualTo("no-store");
        assertThat(cacheControl("/api/v1/users%2Fana")).isEqualTo("no-store");
    }

    private String cacheControl(String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getHeader("Cache-Control");
    }
}
