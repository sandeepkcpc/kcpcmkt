package com.kcpc.mkt.startup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.security.JwtProperties;
import com.kcpc.mkt.security.TokenRegistryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Production performance investigation (2026-08-29) measured a significant JSP-compile +
 * first-query cold-start cost on these specific pages: 0.8-2.9s on the very first hit after a
 * deploy/restart, dropping to ~130-200ms on every hit after that (proven by re-measuring the
 * identical request twice - not assumed). This runner absorbs that one-time cost itself, right
 * after startup, so it lands here instead of on a real user's first request.
 *
 * Runs on its own daemon thread, started from an ApplicationReadyEvent listener (fires only once
 * the whole context - including Flyway/DataSource - has already finished initializing), so it
 * can never delay the app from accepting real traffic; every failure is caught and logged, never
 * rethrown, so a warm-up miss can never crash or destabilize the app.
 *
 * Authenticates the exact same way a real login does - {@link TokenRegistryService#issueAndRegister}
 * is the identical call {@code AuthenticationApplicationService#login} already makes - against
 * whichever real, active, CEO_OWNER-class, non-password-change-pending user currently exists.
 * No password is ever needed (session issuance doesn't require one) and no account is hardcoded;
 * the session this creates is revoked again the moment warm-up finishes, the same footprint an
 * ordinary login-then-logout already leaves in user_sessions.
 */
@Component
@Profile("!test")
public class ApplicationWarmupRunner {

    private static final Logger log = LoggerFactory.getLogger(ApplicationWarmupRunner.class);

    private static final Duration HEALTH_POLL_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration HEALTH_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /** The pages measured with a significant (0.8-2.9s) first-hit cold-start cost. */
    private static final List<String> WARMUP_PATHS = List.of(
            "/app/pipeline",
            "/app/ideas",
            "/app/reports/kpis",
            "/app/reports/admin-actions",
            "/app/audit",
            "/app/admin/catalogue",
            "/app/admin/permissions"
    );

    private final UserRepository userRepository;
    private final TokenRegistryService tokenRegistryService;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final int serverPort;

    public ApplicationWarmupRunner(UserRepository userRepository, TokenRegistryService tokenRegistryService,
                                    JwtProperties jwtProperties, ObjectMapper objectMapper,
                                    @Value("${server.port:8080}") int serverPort) {
        this.userRepository = userRepository;
        this.tokenRegistryService = tokenRegistryService;
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
        this.serverPort = serverPort;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Thread warmupThread = new Thread(this::runWarmup, "app-warmup");
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    private void runWarmup() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        String baseUrl = "http://127.0.0.1:" + serverPort;

        if (!waitForHealthUp(client, baseUrl)) {
            log.warn("[warmup] {}/actuator/health did not report UP within {}s - skipping warm-up",
                    baseUrl, HEALTH_POLL_TIMEOUT.toSeconds());
            return;
        }

        Optional<User> warmupUser = findWarmupUser();
        if (warmupUser.isEmpty()) {
            log.warn("[warmup] no active, CEO_OWNER-class user without a pending forced password "
                    + "change is available to authenticate warm-up requests - skipping warm-up");
            return;
        }

        String jwt;
        try {
            jwt = tokenRegistryService.issueAndRegister(warmupUser.get(), "127.0.0.1", "kcpc-warmup").token();
        } catch (Exception e) {
            log.warn("[warmup] could not issue an internal warm-up session - skipping warm-up", e);
            return;
        }

        int succeeded = 0;
        try {
            for (String path : WARMUP_PATHS) {
                if (warmOne(client, baseUrl, jwt, path)) {
                    succeeded++;
                }
            }
        } finally {
            try {
                tokenRegistryService.revoke(jwt);
            } catch (Exception e) {
                log.warn("[warmup] failed to revoke the internal warm-up session (non-fatal)", e);
            }
        }
        log.info("[warmup] complete: {}/{} paths warmed successfully", succeeded, WARMUP_PATHS.size());
    }

    private boolean waitForHealthUp(HttpClient client, String baseUrl) {
        Instant deadline = Instant.now().plus(HEALTH_POLL_TIMEOUT);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health"))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        while (Instant.now().isBefore(deadline)) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode body = objectMapper.readTree(response.body());
                    if ("UP".equals(body.path("status").asText())) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Expected while Tomcat is still binding its port right after container start -
                // keep polling until the deadline rather than treating one failed attempt as fatal.
            }
            try {
                Thread.sleep(HEALTH_POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** No hardcoded account: picks whichever real, active, CEO_OWNER-class user currently exists
     * and isn't mid a forced password change (which would redirect every /app/** page to the
     * Change Password screen instead of the page being warmed - see ForcePasswordChangeInterceptor). */
    private Optional<User> findWarmupUser() {
        return userRepository.findByActiveTrueOrderByFullNameAsc().stream()
                .filter(u -> u.resolvedAccessClass() == AccessClass.CEO_OWNER)
                .filter(u -> !u.isPasswordChangeRequired())
                .findFirst();
    }

    private boolean warmOne(HttpClient client, String baseUrl, String jwt, String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Cookie", jwtProperties.getCookieName() + "=" + jwt)
                .header("X-Warmup", "true")
                .GET()
                .build();
        long start = System.nanoTime();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                log.info("[warmup] {} -> HTTP {} in {}ms", path, response.statusCode(), elapsedMs);
                return true;
            }
            log.warn("[warmup] {} -> HTTP {} in {}ms (unexpected status, continuing)", path, response.statusCode(), elapsedMs);
            return false;
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            log.warn("[warmup] {} failed after {}ms (non-fatal, continuing): {}", path, elapsedMs, e.toString());
            return false;
        }
    }
}
