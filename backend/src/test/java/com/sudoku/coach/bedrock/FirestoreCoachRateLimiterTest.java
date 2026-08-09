package com.sudoku.coach.bedrock;

import com.google.cloud.firestore.Firestore;
import com.sudoku.game.persistence.FirestoreEmulatorProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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

    @Inject
    Firestore firestore;

    private FirestoreCoachRateLimiter limiter;

    @BeforeEach
    void setUp() throws Exception {
        limiter = new FirestoreCoachRateLimiter();
        set("firestore", firestore);
        set("perMinuteLimit", LIMIT);
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
}
