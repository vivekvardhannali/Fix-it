package com.fixit.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * TESTING-CONVENIENCE (per-tab sessions) - see NOTES_AND_OPEN_DECISIONS.md D23 for why this exists and how to remove it.
 *
 * A browser keeps ONE session cookie per site, so two tabs cannot be logged in as two different people. When enabled
 * ({@code app.auth.per-tab-sessions=true}; on in the "dev" profile, off by default) login/sign-up also hand out a random
 * token; the website keeps it in the tab's own sessionStorage and sends it as {@code Authorization: Bearer <token>}.
 * A valid token identifies the caller and wins over the shared cookie, so every tab can be a different account.
 *
 * Tokens are 256 random bits, kept only as SHA-256 hashes in memory (a heap dump does not reveal usable tokens),
 * expire after {@code app.auth.tab-session-idle-hours} (default 12) without use, and are lost on a server restart.
 */
@Component
public class TabSessionService {

    public record Identity(Long userId, String name) {
    }

    private record Entry(Identity identity, Instant lastUsed) {
    }

    private static final int PRUNE_ABOVE = 10_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean enabled;
    private final Duration idle;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> sessions = new ConcurrentHashMap<>();

    @Autowired
    public TabSessionService(@Value("${app.auth.per-tab-sessions:false}") boolean enabled,
            @Value("${app.auth.tab-session-idle-hours:12}") int idleHours) {
        this(enabled, Duration.ofHours(idleHours), Clock.systemUTC());
    }

    TabSessionService(boolean enabled, Duration idle, Clock clock) {
        this.enabled = enabled;
        this.idle = idle;
        this.clock = clock;
    }

    public boolean enabled() {
        return enabled;
    }

    /** A new token for this user, or null when the feature is off. */
    public String issue(Long userId, String name) {
        if (!enabled) return null;
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(hash(token), new Entry(new Identity(userId, name), clock.instant()));
        if (sessions.size() > PRUNE_ABOVE) {
            Instant now = clock.instant();
            sessions.values().removeIf(e -> e.lastUsed().plus(idle).isBefore(now));
        }
        return token;
    }

    /** Who the token belongs to; empty if the feature is off, the token is unknown, revoked or idle for too long. */
    public Optional<Identity> resolve(String token) {
        if (!enabled || token == null || token.isBlank()) return Optional.empty();
        String key = hash(token);
        Entry entry = sessions.get(key);
        if (entry == null) return Optional.empty();
        Instant now = clock.instant();
        if (entry.lastUsed().plus(idle).isBefore(now)) {
            sessions.remove(key);
            return Optional.empty();
        }
        sessions.put(key, new Entry(entry.identity(), now));        // sliding expiry
        return Optional.of(entry.identity());
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) sessions.remove(hash(token));
    }

    /** For tests. */
    public void clear() {
        sessions.clear();
    }

    /** The token in "Authorization: Bearer <token>", or null. */
    public static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = header.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
