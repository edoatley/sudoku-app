package com.sudoku.admin.persistence;

import com.google.cloud.firestore.Firestore;
import com.sudoku.domain.CandidatesGrid;
import com.sudoku.domain.Grid;
import com.sudoku.game.GameStatus;
import com.sudoku.game.persistence.FirestoreEmulatorProfile;
import com.sudoku.game.persistence.FirestoreGameRepository;
import com.sudoku.game.web.GameState;
import com.sudoku.player.persistence.FirestorePlayerRepository;
import com.sudoku.player.web.PlayerProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for the Firestore admin data adapter against the Dev Services emulator. Seeds the
 * games/players collections via the sibling Firestore repositories (the production write path), then
 * reads them back through the admin adapter.
 *
 * @spec UM-GCP-010
 */
@QuarkusTest
@TestProfile(FirestoreEmulatorProfile.class)
class FirestoreAdminDataRepositoryTest {

    @Inject
    Firestore firestore;

    private FirestoreAdminDataRepository repo;
    private FirestoreGameRepository gameRepo;
    private FirestorePlayerRepository playerRepo;

    @BeforeEach
    void setUp() throws Exception {
        repo = new FirestoreAdminDataRepository();
        inject(FirestoreAdminDataRepository.class, repo);
        gameRepo = new FirestoreGameRepository();
        inject(FirestoreGameRepository.class, gameRepo);
        playerRepo = new FirestorePlayerRepository();
        inject(FirestorePlayerRepository.class, playerRepo);
    }

    private void inject(Class<?> type, Object instance) throws Exception {
        Field f = type.getDeclaredField("firestore");
        f.setAccessible(true);
        f.set(instance, firestore);
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

    private static GameState game(String userId, String gameId) {
        Grid g = emptyGrid();
        return new GameState(userId, gameId, "medium", g, g, g, emptyCandidates(),
                0, GameStatus.IN_PROGRESS.getValue(), 0, "2026-01-01T00:00:00Z", null, 0);
    }

    private static PlayerProfile profile(String userId) {
        return new PlayerProfile(userId, "bob@gmail.com", "Bob", "cat",
                "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z", true, 0L, "2026-01");
    }

    @Test
    void findAllGames_returnsSeededGamesMappedToGameState() {
        String user = "u-" + UUID.randomUUID();
        String gameId = "g-" + UUID.randomUUID();
        gameRepo.save(game(user, gameId));

        List<GameState> games = repo.findAllGames();

        GameState found = games.stream()
                .filter(g -> gameId.equals(g.gameId()))
                .findFirst()
                .orElseThrow();
        assertEquals(user, found.userId());
        assertEquals("medium", found.difficulty());
        assertEquals(9, found.currentGrid().rows().size());
    }

    @Test
    void findAllPlayers_returnsSeededPlayersMappedToProfile() {
        String user = "u-" + UUID.randomUUID();
        playerRepo.upsert(profile(user));

        List<PlayerProfile> players = repo.findAllPlayers();

        PlayerProfile found = players.stream()
                .filter(p -> user.equals(p.userId()))
                .findFirst()
                .orElseThrow();
        assertEquals("bob@gmail.com", found.email());
        assertEquals("Bob", found.displayName());
        assertTrue(found.aiCoachEnabled());
    }
}
