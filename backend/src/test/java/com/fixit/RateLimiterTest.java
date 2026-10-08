package com.fixit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.fixit.exception.TooManyRequestsException;
import com.fixit.security.RateLimiter;

/** The sliding-window limiter alone, with a controllable clock. */
class RateLimiterTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advance(Duration d) { now = now.plus(d); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static RateLimiter limiter(int search, int writes, int registrations, Clock clock) {
        try {   // the clock constructor is package-private (for tests)
            var ctor = RateLimiter.class.getDeclaredConstructor(int.class, int.class, int.class, Clock.class);
            ctor.setAccessible(true);
            return ctor.newInstance(search, writes, registrations, clock);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void allowsUpToTheLimit_thenRefusesWithARetryAfter() {
        var clock = new MutableClock();
        var limiter = limiter(3, 2, 2, clock);
        for (int i = 0; i < 3; i++) limiter.check(RateLimiter.SEARCH, "7");
        assertThatThrownBy(() -> limiter.check(RateLimiter.SEARCH, "7"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e -> {
                    assertThat(e.getRetryAfterSeconds()).isBetween(55L, 61L);
                    assertThat(e.getMessage()).startsWith("Too many requests. Try again in ");
                });
    }

    @Test
    void thereIsRoomAgainAsSoonAsTheOldestRequestLeavesTheWindow() {
        var clock = new MutableClock();
        var limiter = limiter(2, 2, 2, clock);
        limiter.check(RateLimiter.SEARCH, "7");                    // t = 0
        clock.advance(Duration.ofSeconds(40));
        limiter.check(RateLimiter.SEARCH, "7");                    // t = 40
        assertThatThrownBy(() -> limiter.check(RateLimiter.SEARCH, "7")).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofSeconds(21));                     // t = 61: the first one (t = 0) has expired, the second has not
        assertThatCode(() -> limiter.check(RateLimiter.SEARCH, "7")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.check(RateLimiter.SEARCH, "7")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void usersAndKindsOfRequestAreCountedSeparately() {
        var limiter = limiter(1, 1, 1, new MutableClock());
        limiter.check(RateLimiter.SEARCH, "7");
        assertThatCode(() -> limiter.check(RateLimiter.SEARCH, "8")).doesNotThrowAnyException();             // other user
        assertThatCode(() -> limiter.check(RateLimiter.QUESTION_WRITE, "7")).doesNotThrowAnyException();     // other kind
        assertThatThrownBy(() -> limiter.check(RateLimiter.SEARCH, "7")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void registrationsAreLimitedPerAddressPerHour() {
        var clock = new MutableClock();
        var limiter = limiter(5, 5, 2, clock);
        limiter.check(RateLimiter.REGISTER, "10.0.0.1");
        limiter.check(RateLimiter.REGISTER, "10.0.0.1");
        assertThatThrownBy(() -> limiter.check(RateLimiter.REGISTER, "10.0.0.1"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e -> assertThat(e.getRetryAfterSeconds()).isBetween(3590L, 3601L));
        assertThatCode(() -> limiter.check(RateLimiter.REGISTER, "10.0.0.2")).doesNotThrowAnyException();
        clock.advance(Duration.ofMinutes(61));
        assertThatCode(() -> limiter.check(RateLimiter.REGISTER, "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void aRefusedRequestDoesNotExtendTheBlock() {
        var clock = new MutableClock();
        var limiter = limiter(1, 1, 1, clock);
        limiter.check(RateLimiter.SEARCH, "7");                    // t = 0
        for (int i = 0; i < 5; i++) {                              // hammering while blocked...
            clock.advance(Duration.ofSeconds(10));
            assertThatThrownBy(() -> limiter.check(RateLimiter.SEARCH, "7")).isInstanceOf(TooManyRequestsException.class);
        }
        clock.advance(Duration.ofSeconds(11));                     // t = 61: ...still frees up on schedule
        assertThatCode(() -> limiter.check(RateLimiter.SEARCH, "7")).doesNotThrowAnyException();
    }
}
