package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.fixit.security.TabSessionService;

/** The token store alone (TESTING-CONVENIENCE, D23): expiry, revocation, off switch. */
class TabSessionServiceTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static TabSessionService service(boolean enabled, Duration idle, Clock clock) {
        try {
            var ctor = TabSessionService.class.getDeclaredConstructor(boolean.class, Duration.class, Clock.class);
            ctor.setAccessible(true);
            return ctor.newInstance(enabled, idle, clock);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void issuedTokensResolveToTheirOwner_andAreUnique() {
        var s = service(true, Duration.ofHours(12), new MutableClock());
        String a = s.issue(1L, "alice"), b = s.issue(2L, "bob"), a2 = s.issue(1L, "alice");
        assertThat(java.util.Set.of(a, b, a2)).hasSize(3);
        assertThat(s.resolve(a)).contains(new TabSessionService.Identity(1L, "alice"));
        assertThat(s.resolve(b)).contains(new TabSessionService.Identity(2L, "bob"));
        assertThat(s.resolve("nope")).isEmpty();
        assertThat(s.resolve(null)).isEmpty();
        assertThat(s.resolve("  ")).isEmpty();
    }

    @Test
    void revokedTokensStopWorking() {
        var s = service(true, Duration.ofHours(12), new MutableClock());
        String token = s.issue(1L, "alice");
        s.revoke(token);
        assertThat(s.resolve(token)).isEmpty();
        s.revoke("unknown");                                               // harmless
        s.revoke(null);
    }

    @Test
    void tokensExpireAfterBeingIdle_butUseKeepsThemAlive() {
        var clock = new MutableClock();
        var s = service(true, Duration.ofHours(12), clock);
        String token = s.issue(1L, "alice");
        clock.advance(Duration.ofHours(11));
        assertThat(s.resolve(token)).isPresent();                          // used at hour 11 -> renewed
        clock.advance(Duration.ofHours(11));
        assertThat(s.resolve(token)).isPresent();                          // 11 hours since last use: still fine
        clock.advance(Duration.ofHours(13));
        assertThat(s.resolve(token)).isEmpty();                            // idle too long
    }

    @Test
    void whenDisabledNothingIsIssuedOrResolved() {
        var s = service(false, Duration.ofHours(12), new MutableClock());
        assertThat(s.enabled()).isFalse();
        assertThat(s.issue(1L, "alice")).isNull();
        assertThat(s.resolve("anything")).isEmpty();
    }
}
