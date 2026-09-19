package com.sudoku.coach.bedrock;

import com.google.cloud.firestore.Firestore;
import com.sudoku.game.persistence.FirestoreEmulatorProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for the Firestore coach rate limiter against the Dev Services emulator.
 *
 * @spec SC-RL-003, SC-RL-004
 */
@QuarkusTest
@TestProfile(FirestoreEmulatorProfile.class)
class FirestoreCoachRateLimiterTest {

    private static final int LIMIT = 2;

    /** Deliberately 59 seconds into a minute: the instant a wall-clock test is most likely to
     * cross a window boundary part-way through. Pinned, so it cannot. */
    private static final Instant PINNED = Instant.parse("2026-09-19T10:15:59Z");

    @Inject
    Firestore firestore;

    private FirestoreCoachRateLimiter limiter;

    @BeforeEach
    void setUp() throws Exception {
        limiter = new FirestoreCoachRateLimiter();
        set("firestore", firestore);
        set("perMinuteLimit", LIMIT);
        // The counter is keyed per UTC minute and read from the clock on every call, so a test
        // whose calls straddle a boundary sees it reset mid-assertion. Pinning removes the race
        // without widening the window the limiter enforces.
        pinClockTo(PINNED);
    }

    private void pinClockTo(Instant instant) throws Exception {
        set("clock", Clock.fixed(instant, ZoneOffset.UTC));
    }

    private void set(String name, Object value) throws Exception {
        Field f = FirestoreCoachRateLimiter.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(limiter, value);
    }

    @Test
    void allowsUpToLimitThenRejectsWithinSameWindow() {
        String user = "u-" + UUID.randomUUID();

        assertTrue(limiter.tryConsume(user), "1st call allowed");
        assertTrue(limiter.tryConsume(user), "2nd call allowed (at limit)");
        assertFalse(limiter.tryConsume(user), "3rd call rejected once limit reached");
        assertFalse(limiter.tryConsume(user), "still rejected");
    }

    @Test
    void limitIsPerUser() {
        String a = "u-" + UUID.randomUUID();
        String b = "u-" + UUID.randomUUID();

        assertTrue(limiter.tryConsume(a));
        assertTrue(limiter.tryConsume(a));
        assertFalse(limiter.tryConsume(a), "user a exhausted");

        assertTrue(limiter.tryConsume(b), "user b unaffected by user a");
    }

    @Test
    void limitResetsInTheNextWindow() throws Exception {
        // The windowing SC-RL-003 specifies, asserted rather than assumed: exhausting the limit
        // must not outlive the minute it was exhausted in. Previously untestable, because the
        // clock could not be moved.
        String user = "u-" + UUID.randomUUID();

        assertTrue(limiter.tryConsume(user));
        assertTrue(limiter.tryConsume(user));
        assertFalse(limiter.tryConsume(user), "exhausted within the window");

        pinClockTo(PINNED.plusSeconds(1)); // one second later, but the next UTC minute
        assertTrue(limiter.tryConsume(user), "a new window starts with a fresh count");
    }

    @Test
    void theWindowIsTheUtcMinuteNotAFixedInterval() throws Exception {
        // Two calls in the same minute share a window even when seconds apart; the boundary is
        // the minute, not "60 seconds since the first call".
        String user = "u-" + UUID.randomUUID();

        pinClockTo(Instant.parse("2026-09-19T10:15:01Z"));
        assertTrue(limiter.tryConsume(user));
        pinClockTo(Instant.parse("2026-09-19T10:15:58Z"));
        assertTrue(limiter.tryConsume(user));
        assertFalse(limiter.tryConsume(user), "same minute, so the limit still applies");
    }
}
