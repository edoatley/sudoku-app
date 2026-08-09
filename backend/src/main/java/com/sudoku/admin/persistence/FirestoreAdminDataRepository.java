package com.sudoku.admin.persistence;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.sudoku.admin.AdminDataRepository;
import com.sudoku.game.persistence.GameItem;
import com.sudoku.game.web.GameState;
import com.sudoku.player.persistence.PlayerItem;
import com.sudoku.player.web.PlayerProfile;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Firestore-backed admin data browser (GCP adapter). Reads the whole {@code games} and
 * {@code players} collections and reuses the {@link GameItem}/{@link PlayerItem} document models
 * (the same POJOs the game/player Firestore repositories persist), mapping each to the API shape so
 * the response matches the DynamoDB adapter exactly.
 *
 * <p>Selected at runtime via {@code sudoku.persistence=firestore}. Full-collection reads are
 * acceptable given the low data volumes and restricted admin audience — same trade-off as the AWS
 * table scans.
 *
 * @spec UM-GCP-010
 */
@ApplicationScoped
@Typed(FirestoreAdminDataRepository.class)
@LookupIfProperty(name = "sudoku.persistence", stringValue = "firestore")
public class FirestoreAdminDataRepository implements AdminDataRepository {

    static final String GAMES_COLLECTION = "games";
    static final String PLAYERS_COLLECTION = "players";

    @Inject
    Firestore firestore;

    @Override
    public List<GameState> findAllGames() {
        return await(firestore.collection(GAMES_COLLECTION).get()).getDocuments().stream()
                .map(d -> d.toObject(GameItem.class).toGameState())
                .toList();
    }

    @Override
    public List<PlayerProfile> findAllPlayers() {
        return await(firestore.collection(PLAYERS_COLLECTION).get()).getDocuments().stream()
                .map(d -> d.toObject(PlayerItem.class).toPlayerProfile())
                .toList();
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Firestore operation interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Firestore operation failed", e.getCause());
        }
    }
}
