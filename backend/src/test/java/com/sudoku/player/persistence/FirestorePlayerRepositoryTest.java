package com.sudoku.player.persistence;

import com.google.cloud.firestore.Firestore;
import com.sudoku.game.persistence.FirestoreEmulatorProfile;
import com.sudoku.player.web.PlayerProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for the Firestore player adapter against the Dev Services emulator.
 *
 * @spec UM-GCP-007
 */
@QuarkusTest
@TestProfile(FirestoreEmulatorProfile.class)
class FirestorePlayerRepositoryTest {

    @Inject
    Firestore firestore;

    private FirestorePlayerRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        repo = new FirestorePlayerRepository();
        Field f = FirestorePlayerRepository.class.getDeclaredField("firestore");
        f.setAccessible(true);
        f.set(repo, firestore);
    }

    private static PlayerProfile profile(String userId) {
        return new PlayerProfile(userId, "bob@gmail.com", "Bob", "cat",
                "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", true, 0L, "2026-01");
    }

    @Test
    void upsert_then_findById_roundTrips() {
        String user = "u-" + UUID.randomUUID();
        repo.upsert(profile(user));

        PlayerProfile loaded = repo.findById(user).orElseThrow();
        assertEquals(user, loaded.userId());
        assertEquals("bob@gmail.com", loaded.email());
        assertEquals("Bob", loaded.displayName());
        assertTrue(loaded.aiCoachEnabled());
    }

    @Test
    void findById_absent_isEmpty() {
        assertFalse(repo.findById("nobody-" + UUID.randomUUID()).isPresent());
    }

    @Test
    void upsert_overwrites() {
        String user = "u-" + UUID.randomUUID();
        repo.upsert(profile(user));
        repo.upsert(new PlayerProfile(user, "bob@gmail.com", "Bobby", "dog",
                "2026-01-01T00:00:00Z", "2026-02-02T00:00:00Z", false, 0L, "2026-02"));

        PlayerProfile loaded = repo.findById(user).orElseThrow();
        assertEquals("Bobby", loaded.displayName());
        assertFalse(loaded.aiCoachEnabled());
    }

    @Test
    void incrementCoachTokens_accumulatesWithinMonth() {
        String user = "u-" + UUID.randomUUID();
        repo.upsert(profile(user));

        repo.incrementCoachTokens(user, 100, "2026-01");
        repo.incrementCoachTokens(user, 50, "2026-01");

        Optional<PlayerProfile> loaded = repo.findById(user);
        assertEquals(150L, loaded.orElseThrow().coachTokensUsedThisMonth());
    }
}
