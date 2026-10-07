package com.fixit.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fixit.exception.TooManyRequestsException;

/**
 * Slows down password guessing. After too many FAILED logins for one account name (default 5) or from one IP address
 * (default 20) within the window (default 15 minutes), further attempts are refused (HTTP 429, Retry-After) until the
 * window passes - even if the next password is right. Unknown account names are counted the same way, so the behaviour
 * reveals nothing about which accounts exist. In-memory: resets on restart and is per server instance (fine for a single
 * server; behind a reverse proxy, make the proxy pass the real client address).
 */
@Component
public class LoginThrottle {

    private static final int PRUNE_ABOVE = 10_000;

    private record Entry(int failures, Instant windowStart, Instant lockedUntil) {
    }

    private final int maxPerAccount;
    private final int maxPerIp;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Autowired
    public LoginThrottle(@Value("${app.auth.login-max-attempts:5}") int maxPerAccount,
            @Value("${app.auth.login-max-attempts-per-ip:20}") int maxPerIp,
            @Value("${app.auth.login-window-minutes:15}") int windowMinutes) {
        this(maxPerAccount, maxPerIp, Duration.ofMinutes(windowMinutes), Clock.systemUTC());
    }

    LoginThrottle(int maxPerAccount, int maxPerIp, Duration window, Clock clock) {
        this.maxPerAccount = maxPerAccount;
        this.maxPerIp = maxPerIp;
        this.window = window;
        this.clock = clock;
    }

    /** @throws TooManyRequestsException if this account name or this address is currently locked */
    public void checkAllowed(String account, String ip) {
        Instant now = clock.instant();
        for (String key : new String[] { accountKey(account), ipKey(ip) }) {
            Entry e = entries.get(key);
            if (e != null && e.lockedUntil() != null && e.lockedUntil().isAfter(now)) {
                long seconds = Math.max(1, Duration.between(now, e.lockedUntil()).toSeconds());
                throw new TooManyRequestsException("Too many failed login attempts. Try again later.", seconds);
            }
        }
    }

    public void recordFailure(String account, String ip) {
        record(accountKey(account), maxPerAccount);
        record(ipKey(ip), maxPerIp);
        if (entries.size() > PRUNE_ABOVE) {
            Instant now = clock.instant();
            entries.values().removeIf(e -> e.windowStart().plus(window).isBefore(now)
                    && (e.lockedUntil() == null || e.lockedUntil().isBefore(now)));
        }
    }

    /** A correct password clears the account's counter (the IP's counter keeps running). */
    public void recordSuccess(String account) {
        entries.remove(accountKey(account));
    }

    /** For tests. */
    public void clear() {
        entries.clear();
    }

    private void record(String key, int max) {
        Instant now = clock.instant();
        entries.compute(key, (k, old) -> {
            if (old == null || old.windowStart().plus(window).isBefore(now)) {
                old = new Entry(0, now, null);
            }
            int failures = old.failures() + 1;
            Instant lockedUntil = failures >= max ? now.plus(window) : old.lockedUntil();
            return new Entry(failures, old.windowStart(), lockedUntil);
        });
    }

    private static String accountKey(String account) {
        return "account:" + account.trim().toLowerCase();
    }

    private static String ipKey(String ip) {
        return "ip:" + (ip == null ? "unknown" : ip);
    }
}
