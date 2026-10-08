package com.fixit.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fixit.exception.TooManyRequestsException;

/**
 * Sliding-window request limits that protect the calls which cost money or can be abused:
 * <ul>
 *   <li>{@link #SEARCH} - each search makes one embedding request (default 30 per user per minute),</li>
 *   <li>{@link #QUESTION_WRITE} - creating/editing a question makes one embedding request (default 10 per user per minute),</li>
 *   <li>{@link #REGISTER} - per IP address, so minting many accounts cannot dodge the per-user limits (default 10 per hour).</li>
 * </ul>
 * Every call to {@link #check} counts, valid or not. In memory: resets on restart and is per server instance. Behind a
 * reverse proxy, make the proxy pass the real client address (registration is limited by remote address).
 */
@Component
public class RateLimiter {

    public static final String SEARCH = "search";
    public static final String QUESTION_WRITE = "question-write";
    public static final String REGISTER = "register";

    private static final int PRUNE_ABOVE = 10_000;

    private record Rule(int max, Duration window) {
    }

    private final java.util.Map<String, Rule> rules;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(@Value("${app.rate-limit.search-per-minute:30}") int searchPerMinute,
            @Value("${app.rate-limit.question-writes-per-minute:10}") int questionWritesPerMinute,
            @Value("${app.rate-limit.registrations-per-ip-per-hour:10}") int registrationsPerIpPerHour) {
        this(searchPerMinute, questionWritesPerMinute, registrationsPerIpPerHour, Clock.systemUTC());
    }

    RateLimiter(int searchPerMinute, int questionWritesPerMinute, int registrationsPerIpPerHour, Clock clock) {
        this.rules = java.util.Map.of(
                SEARCH, new Rule(searchPerMinute, Duration.ofMinutes(1)),
                QUESTION_WRITE, new Rule(questionWritesPerMinute, Duration.ofMinutes(1)),
                REGISTER, new Rule(registrationsPerIpPerHour, Duration.ofHours(1)));
        this.clock = clock;
    }

    /**
     * Counts one request of the named kind for {@code key} (a user id, or an IP address for {@link #REGISTER}).
     *
     * @throws TooManyRequestsException (HTTP 429 + Retry-After) when the limit for the window is already used up
     */
    public void check(String limit, String key) {
        Rule rule = rules.get(limit);
        Instant now = clock.instant();
        Deque<Instant> window = events.computeIfAbsent(limit + ":" + key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && !window.peekFirst().plus(rule.window()).isAfter(now)) {
                window.pollFirst();                                       // outside the window
            }
            if (window.size() >= rule.max()) {
                long seconds = Math.max(1, Duration.between(now, window.peekFirst().plus(rule.window())).toSeconds() + 1);
                throw new TooManyRequestsException("Too many requests. Try again in " + seconds + " second"
                        + (seconds == 1 ? "" : "s") + ".", seconds);
            }
            window.addLast(now);
        }
        if (events.size() > PRUNE_ABOVE) {
            events.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || e.getValue().peekLast().plus(Duration.ofHours(1)).isBefore(now);
                }
            });
        }
    }

    /** For tests. */
    public void clear() {
        events.clear();
    }
}
