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
import com.fixit.security.LoginThrottle;

/** The failed-login limiter alone, with a controllable clock. */
class LoginThrottleTest {

    /** A clock the test can move forward. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static LoginThrottle throttle(MutableClock clock) {
        return newThrottle(3, 5, Duration.ofMinutes(15), clock);
    }

    // the production constructor is package-private for tests: reach it reflectively
    private static LoginThrottle newThrottle(int perAccount, int perIp, Duration window, Clock clock) {
        try {
            var ctor = LoginThrottle.class.getDeclaredConstructor(int.class, int.class, Duration.class, Clock.class);
            ctor.setAccessible(true);
            return ctor.newInstance(perAccount, perIp, window, clock);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void locksAnAccountAfterTheMaximumNumberOfFailures_andReportsHowLongToWait() {
        var clock = new MutableClock();
        var t = throttle(clock);
        for (int i = 0; i < 2; i++) {
            t.checkAllowed("alice", "1.1.1.1");
            t.recordFailure("alice", "1.1.1.1");
        }
        assertThatCode(() -> t.checkAllowed("alice", "1.1.1.1")).doesNotThrowAnyException();   // 2 of 3: still allowed
        t.recordFailure("alice", "1.1.1.1");                                                     // 3rd failure
        assertThatThrownBy(() -> t.checkAllowed("alice", "1.1.1.1"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(890L, 900L));
    }

    @Test
    void theLockEndsAfterTheWindow() {
        var clock = new MutableClock();
        var t = throttle(clock);
        for (int i = 0; i < 3; i++) t.recordFailure("alice", "1.1.1.1");
        assertThatThrownBy(() -> t.checkAllowed("alice", "9.9.9.9")).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> t.checkAllowed("alice", "9.9.9.9")).isInstanceOf(TooManyRequestsException.class);
        clock.advance(Duration.ofMinutes(2));
        assertThatCode(() -> t.checkAllowed("alice", "9.9.9.9")).doesNotThrowAnyException();
    }

    @Test
    void accountNamesAreCaseInsensitive_andOtherAccountsAreUnaffected() {
        var t = throttle(new MutableClock());
        for (int i = 0; i < 3; i++) t.recordFailure("Alice", "1.1.1." + i);                   // different IPs
        assertThatThrownBy(() -> t.checkAllowed("ALICE", "8.8.8.8")).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> t.checkAllowed("bob", "8.8.8.8")).doesNotThrowAnyException();
    }

    @Test
    void anIpAddressIsLockedAfterItsOwnLimit_evenAcrossManyAccountNames() {
        var t = throttle(new MutableClock());
        for (int i = 0; i < 5; i++) t.recordFailure("victim" + i, "6.6.6.6");                   // 1 failure per account
        assertThatThrownBy(() -> t.checkAllowed("someone-new", "6.6.6.6")).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> t.checkAllowed("someone-new", "7.7.7.7")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulLoginClearsTheAccountCounter() {
        var t = throttle(new MutableClock());
        t.recordFailure("alice", "1.1.1.1");
        t.recordFailure("alice", "1.1.1.1");
        t.recordSuccess("alice");
        t.recordFailure("alice", "1.1.1.1");
        t.recordFailure("alice", "1.1.1.1");
        assertThatCode(() -> t.checkAllowed("alice", "2.2.2.2")).doesNotThrowAnyException();    // 2 since the reset, not 4
    }

    @Test
    void failuresOlderThanTheWindowAreForgotten() {
        var clock = new MutableClock();
        var t = throttle(clock);
        t.recordFailure("alice", "1.1.1.1");
        t.recordFailure("alice", "1.1.1.1");
        clock.advance(Duration.ofMinutes(16));
        t.recordFailure("alice", "1.1.1.1");                                                     // counts as the first again
        assertThatCode(() -> t.checkAllowed("alice", "2.2.2.2")).doesNotThrowAnyException();
    }
}
