package com.sudoku.leaderboard.persistence;

import com.google.cloud.firestore.Firestore;
import com.sudoku.game.persistence.FirestoreEmulatorProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for the Firestore leaderboard adapter against the Dev Services emulator.
 *
 * @spec LT-GCP-002, LT-GCP-003, LT-GCP-004, LT-GCP-005, LT-GCP-006, LT-GCP-007, LT-GCP-008
 */
@QuarkusTest
@TestProfile(FirestoreEmulatorProfile.class)
class FirestoreLeaderboardRepositoryTest {

    @Inject
    Firestore firestore;

    private FirestoreLeaderboardRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        repo = new FirestoreLeaderboardRepository();
        Field f = FirestoreLeaderboardRepository.class.getDeclaredField("firestore");
        f.setAccessible(true);
        f.set(repo, firestore);
    }

    private LeaderboardItem load(String userId) {
        return repo.findAll().stream()
                .filter(i -> userId.equals(i.getUserId()))
                .findFirst()
                .orElse(null);
    }

    // @spec LT-GCP-002, LT-GCP-004, LT-GCP-005, LT-GCP-006, LT-GCP-007
    @Test
    void firstWin_createsDocWithUserIdAndCounters() {
        String user = "u-" + UUID.randomUUID();
        repo.updateOnSolve(user, "easy", 120, 90, "won");

        LeaderboardItem item = load(user);
        assertNotNull(item, "document should exist in the leaderboard collection");
        assertEquals(user, item.getUserId(), "userId must be persisted as a field for the ranking join");
        assertEquals(1, item.getTotalGames());
        assertEquals(1, item.getTotalWins());
        assertEquals(90, item.getTotalScore());
        assertEquals(120, item.getTotalElapsedSeconds());
        assertEquals(120, item.getBestEasySeconds().intValue());
        assertNotNull(item.getUpdatedAt());
    }

    // @spec LT-GCP-005
    @Test
    void nonWin_incrementsOnlyTotalGames() {
        String user = "u-" + UUID.randomUUID();
        repo.updateOnSolve(user, "medium", 300, 0, "abandoned");

        LeaderboardItem item = load(user);
        assertEquals(1, item.getTotalGames());
        assertEquals(0, item.getTotalWins());
        assertEquals(0, item.getTotalScore());
        assertEquals(0, item.getTotalElapsedSeconds());
        assertNull(item.getBestMediumSeconds());
        assertNotNull(item.getUpdatedAt());
    }

    // @spec LT-GCP-003, LT-GCP-006
    @Test
    void repeatedWins_accumulateCounters() {
        String user = "u-" + UUID.randomUUID();
        repo.updateOnSolve(user, "medium", 200, 100, "won");
        repo.updateOnSolve(user, "medium", 250, 80, "won");

        LeaderboardItem item = load(user);
        assertEquals(2, item.getTotalGames());
        assertEquals(2, item.getTotalWins());
        assertEquals(180, item.getTotalScore());
        assertEquals(450, item.getTotalElapsedSeconds());
    }

    // @spec LT-GCP-007
    @Test
    void bestTime_keepsMinimumAcrossWins() {
        String user = "u-" + UUID.randomUUID();
        repo.updateOnSolve(user, "hard", 500, 50, "won");
        repo.updateOnSolve(user, "hard", 400, 60, "won"); // faster -> updates best
        repo.updateOnSolve(user, "hard", 450, 55, "won"); // slower -> no change

        LeaderboardItem item = load(user);
        assertEquals(400, item.getBestHardSeconds().intValue());
    }

    // @spec LT-GCP-007
    @Test
    void unknownDifficulty_mapsToMedium() {
        String user = "u-" + UUID.randomUUID();
        repo.updateOnSolve(user, "wibble", 210, 70, "won");

        LeaderboardItem item = load(user);
        assertEquals(210, item.getBestMediumSeconds().intValue());
        assertNull(item.getBestEasySeconds());
    }

    // @spec LT-GCP-008
    @Test
    void findAll_returnsAllPlayers() {
        String a = "u-" + UUID.randomUUID();
        String b = "u-" + UUID.randomUUID();
        repo.updateOnSolve(a, "easy", 100, 90, "won");
        repo.updateOnSolve(b, "easy", 110, 85, "won");

        List<LeaderboardItem> all = repo.findAll();
        assertTrue(all.stream().anyMatch(i -> a.equals(i.getUserId())));
        assertTrue(all.stream().anyMatch(i -> b.equals(i.getUserId())));
    }
}
