package com.devrats.security;

import com.devrats.config.AppProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final AppProperties properties;
    private final SecurityErrorWriter errorWriter;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    public RateLimitFilter(AppProperties properties, SecurityErrorWriter errorWriter) {
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = normalizePath(request.getRequestURI());
        String ip = clientIp(request);
        if (path.startsWith("/api/v1/")) {
            Bucket global = buckets.get("global:" + ip, key -> bucket(
                    properties.getRateLimit().getGlobalCapacity(), properties.getRateLimit().getGlobalRefill()));
            if (!consume(global, response)) {
                return;
            }
        }

        Bucket specific = resolveSpecificBucket(path, request.getMethod(), ip);
        if (specific != null && !consume(specific, response)) {
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean consume(Bucket bucket, HttpServletResponse response) throws IOException {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return true;
        }
        long retryAfter = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        errorWriter.write(response, 429, "Muitas requisições", "Tente novamente em alguns instantes");
        return false;
    }

    /**
     * Buckets por IP com paths exatos.
     *
     * <p>Não usar {@code startsWith("/api/v1/friends")} para o envio: accept/decline/cancel
     * são POST/DELETE em subrotas e não devem consumir o bucket de criação.</p>
     */
    private Bucket resolveSpecificBucket(String path, String method, String ip) {
        if (path.equals("/api/v1/me/avatar")) {
            return buckets.get("avatar:" + ip, key -> bucket(10, Duration.ofMinutes(1)));
        }
        if (path.startsWith("/api/v1/auth/")) {
            return buckets.get("auth:" + ip, key -> bucket(properties.getRateLimit().getAuthCapacity(),
                    properties.getRateLimit().getAuthRefill()));
        }
        if (path.equals("/api/v1/webhooks/github")) {
            return buckets.get("webhook:" + ip, key -> bucket(properties.getRateLimit().getWebhookCapacity(),
                    properties.getRateLimit().getWebhookRefill()));
        }
        if ("GET".equals(method) && path.equals("/api/v1/users/search")) {
            return buckets.get("search:" + ip, key -> bucket(properties.getRateLimit().getSearchCapacity(),
                    properties.getRateLimit().getSearchRefill()));
        }
        if ("POST".equals(method) && path.equals("/api/v1/friends/requests")) {
            return buckets.get("friend-request:" + ip, key -> bucket(
                    properties.getRateLimit().getFriendRequestCapacity(),
                    properties.getRateLimit().getFriendRequestRefill()));
        }
        if ("POST".equals(method) && path.equals("/api/v1/presence/heartbeat")) {
            return buckets.get("heartbeat:" + ip, key -> bucket(properties.getRateLimit().getHeartbeatCapacity(),
                    properties.getRateLimit().getHeartbeatRefill()));
        }
        return null;
    }

    private Bucket bucket(int capacity, Duration refill) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, refill).build())
                .build();
    }

    //---- Spring MVC ignora parâmetros de matriz; normalizar evita bypass dos buckets.
    private String normalizePath(String path) {
        return path.replaceAll(";[^/]*", "");
    }

    private String clientIp(HttpServletRequest request) {
        if (properties.getRateLimit().isTrustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
