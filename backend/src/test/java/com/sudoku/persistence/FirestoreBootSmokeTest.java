package com.sudoku.persistence;

import com.sudoku.game.GameRepository;
import com.sudoku.game.persistence.FirestoreGameRepository;
import com.sudoku.leaderboard.LeaderboardRepository;
import com.sudoku.leaderboard.persistence.FirestoreLeaderboardRepository;
import com.sudoku.player.PlayerRepository;
import com.sudoku.player.persistence.FirestorePlayerRepository;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Proves the whole application boots on the GCP persistence backend: with
 * {@code sudoku.persistence=firestore} selected at runtime, the CDI container starts and every
 * repository injection point resolves (via the per-domain producers) to the Firestore/no-op adapter
 * while the DynamoDB adapters stay unresolvable. If any bean in the graph — including out-of-slice
 * ones like the coach rate limiter that inject {@code DynamoDbClient} — failed to wire, this test
 * would fail at container start.
 *
 * @spec GL-GCP-001, UM-GCP-007, LT-GCP-001
 */
@QuarkusTest
@TestProfile(FirestorePersistenceProfile.class)
class FirestoreBootSmokeTest {

    @Inject
    GameRepository gameRepository;

    @Inject
    PlayerRepository playerRepository;

    @Inject
    LeaderboardRepository leaderboardRepository;

    @Test
    void firestoreAdaptersAreSelected() {
        assertInstanceOf(FirestoreGameRepository.class, ClientProxy.unwrap(gameRepository));
        assertInstanceOf(FirestorePlayerRepository.class, ClientProxy.unwrap(playerRepository));
        assertInstanceOf(FirestoreLeaderboardRepository.class, ClientProxy.unwrap(leaderboardRepository));
    }
}
