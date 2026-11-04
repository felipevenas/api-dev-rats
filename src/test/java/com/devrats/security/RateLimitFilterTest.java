package com.devrats.security;

import com.devrats.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class RateLimitFilterTest {

    private final SecurityErrorWriter errorWriter = mock(SecurityErrorWriter.class);
    private final AppProperties properties = new AppProperties();
    private final RateLimitFilter filter = new RateLimitFilter(properties, errorWriter);

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            MockHttpServletResponse response = invocation.getArgument(0);
            response.setStatus(invocation.getArgument(1));
            return null;
        }).when(errorWriter).write(any(), anyInt(), anyString(), anyString());
    }

    @Test
    void shouldApplyFriendRequestBucketToPostOnly() throws Exception {
        properties.getRateLimit().setGlobalCapacity(100);
        properties.getRateLimit().setFriendRequestCapacity(1);
        properties.getRateLimit().setFriendRequestRefill(Duration.ofHours(1));

        assertThat(run("POST", "/api/v1/friends/requests").getStatus()).isEqualTo(200);
        assertThat(run("POST", "/api/v1/friends/requests").getStatus()).isEqualTo(429);
        assertThat(run("POST", "/api/v1/friends/requests/abc/accept").getStatus()).isEqualTo(200);
    }

    @Test
    void shouldNormalizeMatrixParamsBeforeMatchingTheBucket() throws Exception {
        properties.getRateLimit().setGlobalCapacity(100);
        properties.getRateLimit().setFriendRequestCapacity(1);
        properties.getRateLimit().setFriendRequestRefill(Duration.ofHours(1));

        assertThat(run("POST", "/api/v1/friends/requests").getStatus()).isEqualTo(200);
        assertThat(run("POST", "/api/v1/friends/requests;x=1").getStatus()).isEqualTo(429);
    }

    @Test
    void shouldApplySearchBucketToPercentEncodedPath() throws Exception {
        properties.getRateLimit().setGlobalCapacity(100);
        properties.getRateLimit().setSearchCapacity(1);
        properties.getRateLimit().setSearchRefill(Duration.ofMinutes(1));

        assertThat(run("GET", "/api/v1/users/search").getStatus()).isEqualTo(200);
        assertThat(run("GET", "/api/v1/users%2Fsearch").getStatus()).isEqualTo(429);
    }

    @Test
    void shouldExposeRetryAfterOn429() throws Exception {
        properties.getRateLimit().setGlobalCapacity(1);
        properties.getRateLimit().setGlobalRefill(Duration.ofMinutes(1));

        run("GET", "/api/v1/dashboard");
        MockHttpServletResponse response = run("GET", "/api/v1/dashboard");

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isNotBlank();
    }

    private MockHttpServletResponse run(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
