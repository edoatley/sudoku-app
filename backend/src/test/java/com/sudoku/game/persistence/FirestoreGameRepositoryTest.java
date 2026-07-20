package com.sudoku.game.persistence;

import com.google.cloud.firestore.Firestore;
import com.sudoku.domain.CandidatesGrid;
import com.sudoku.domain.Grid;
import com.sudoku.game.GameNotFoundException;
import com.sudoku.game.GameStatus;
import com.sudoku.game.web.GameHistoryEntry;
import com.sudoku.game.web.GameState;
import com.sudoku.game.web.GameUpdateRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for the Firestore game adapter against the Dev Services emulator.
 *
 * @spec GL-GCP-002, GL-GCP-003, GL-GCP-004, GL-GCP-005
 */
@QuarkusTest
@TestProfile(FirestoreEmulatorProfile.class)
class FirestoreGameRepositoryTest {

    @Inject
    Firestore firestore;

    private FirestoreGameRepository repo;

    @BeforeEach
    void setUp() throws Exception {
        repo = new FirestoreGameRepository();
        Field f = FirestoreGameRepository.class.getDeclaredField("firestore");
        f.setAccessible(true);
        f.set(repo, firestore);
    }

    private static Grid emptyGrid() {
        List<List<Integer>> rows = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            rows.add(Collections.nCopies(9, 0));
        }
        return Grid.of(rows);
    }

    private static CandidatesGrid emptyCandidates() {
        List<List<List<Integer>>> rows = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            List<List<Integer>> r = new ArrayList<>();
            for (int j = 0; j < 9; j++) {
                r.add(List.of());
            }
            rows.add(r);
        }
        return CandidatesGrid.of(rows);
    }

    private static GameState game(String userId, String gameId, String status, String endedAt) {
        Grid g = emptyGrid();
        return new GameState(userId, gameId, "medium", g, g, g, emptyCandidates(),
                0, status, 0, "2026-01-01T00:00:00Z", endedAt, 0);
    }

    @Test
    void save_then_findById_roundTrips() {
        String user = "u-" + UUID.randomUUID();
        repo.save(game(user, "g1", GameStatus.IN_PROGRESS.getValue(), null));

        GameState loaded = repo.findById(user, "g1").orElseThrow();
        assertEquals(user, loaded.userId());
        assertEquals("g1", loaded.gameId());
        assertEquals("medium", loaded.difficulty());
        assertEquals(GameStatus.IN_PROGRESS.getValue(), loaded.status());
        assertEquals(9, loaded.currentGrid().rows().size());
    }

    @Test
    void findById_wrongUser_isIdorSafe() {
        String user = "u-" + UUID.randomUUID();
        repo.save(game(user, "g1", GameStatus.IN_PROGRESS.getValue(), null));

        // Another user cannot fetch it even knowing the gameId (composite doc id).
        assertThrows(GameNotFoundException.class, () -> repo.findById("someone-else", "g1"));
    }

    @Test
    void findInProgress_returnsOnlyInProgress() {
        String user = "u-" + UUID.randomUUID();
        repo.save(game(user, "old", GameStatus.ABANDONED.getValue(), "2026-01-02T00:00:00Z"));
        repo.save(game(user, "active", GameStatus.IN_PROGRESS.getValue(), null));

        Optional<GameState> found = repo.findInProgress(user);
        assertTrue(found.isPresent());
        assertEquals("active", found.get().gameId());
    }

    @Test
    void update_appliesChanges() {
        String user = "u-" + UUID.randomUUID();
        repo.save(game(user, "g1", GameStatus.IN_PROGRESS.getValue(), null));

        repo.update(user, "g1", new GameUpdateRequest(emptyGrid(), emptyCandidates(), 120, true, 3));

        GameState updated = repo.findById(user, "g1").orElseThrow();
        assertEquals(120, updated.timeSpentSeconds());
        assertEquals(GameStatus.SOLVED.getValue(), updated.status());
    }

    @Test
    void abandonGame_marksAbandoned() {
        String user = "u-" + UUID.randomUUID();
        repo.save(game(user, "g1", GameStatus.IN_PROGRESS.getValue(), null));

        repo.abandonGame(user, "g1");

        assertEquals(GameStatus.ABANDONED.getValue(), repo.findById(user, "g1").orElseThrow().status());
        assertFalse(repo.findInProgress(user).isPresent());
    }

    @Test
    void findHistory_returnsTerminalGamesNewestFirst() {
        String user = "u-" + UUID.randomUUID();
        repo.save(game(user, "active", GameStatus.IN_PROGRESS.getValue(), null));
        repo.save(game(user, "won", GameStatus.SOLVED.getValue(), "2026-01-03T00:00:00Z"));
        repo.save(game(user, "quit", GameStatus.ABANDONED.getValue(), "2026-01-05T00:00:00Z"));

        List<GameHistoryEntry> history = repo.findHistory(user, 10);

        assertEquals(2, history.size());
        assertEquals("quit", history.get(0).gameId()); // newest endedAt first
        assertEquals("won", history.get(1).gameId());
    }
}
