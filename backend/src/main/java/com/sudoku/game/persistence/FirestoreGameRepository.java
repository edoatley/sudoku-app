package com.sudoku.game.persistence;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.sudoku.game.GameNotFoundException;
import com.sudoku.game.GameRepository;
import com.sudoku.game.GameStatus;
import com.sudoku.game.web.GameHistoryEntry;
import com.sudoku.game.web.GameState;
import com.sudoku.game.web.GameUpdateRequest;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Firestore-backed persistence for Sudoku game sessions (GCP adapter).
 *
 * <p>Selected at build time via {@code sudoku.persistence=firestore}; the DynamoDB adapter is the
 * default. Reuses {@link GameItem} as the document model — Firestore's POJO mapper serialises its
 * getters/setters and ignores the (inert) DynamoDB annotations — so grid JSON serialisation and the
 * {@code applyUpdate}/{@code markAbandoned} mutations are shared with the AWS adapter (no duplication).
 *
 * <p>Each game is a document in the {@code games} collection keyed by {@code <userId>__<gameId>},
 * so a game can only be read with its owner's userId (IDOR-safe, mirroring the DynamoDB composite
 * key). {@code findInProgress}/{@code findHistory} filter {@code status} server-side and rely on the
 * {@code (userId, status, endedAt)} composite index.
 *
 * <p>The single-active-game invariant is orchestrated by {@code GameServiceImpl}
 * (findInProgress → abandon → save), non-atomically, exactly as on AWS.
 *
 * @spec GL-GCP-001, GL-GCP-002, GL-GCP-003, GL-GCP-004, GL-GCP-005
 */
@ApplicationScoped
@IfBuildProperty(name = "sudoku.persistence", stringValue = "firestore")
public class FirestoreGameRepository implements GameRepository {

    static final String COLLECTION = "games";

    @Inject
    Firestore firestore;

    private static String docId(String userId, String gameId) {
        return userId + "__" + gameId;
    }

    private DocumentReference doc(String userId, String gameId) {
        return firestore.collection(COLLECTION).document(docId(userId, gameId));
    }

    @Override
    public void save(GameState gameState) {
        await(doc(gameState.userId(), gameState.gameId()).set(GameItem.from(gameState)));
    }

    @Override
    public Optional<GameState> findById(String userId, String gameId) {
        DocumentSnapshot snap = await(doc(userId, gameId).get());
        if (!snap.exists()) {
            throw new GameNotFoundException(gameId);
        }
        return Optional.of(snap.toObject(GameItem.class).toGameState());
    }

    @Override
    public Optional<GameState> findInProgress(String userId) {
        QuerySnapshot qs = await(firestore.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", GameStatus.IN_PROGRESS.getValue())
                .limit(1)
                .get());
        return qs.getDocuments().stream()
                .findFirst()
                .map(d -> d.toObject(GameItem.class).toGameState());
    }

    @Override
    public void update(String userId, String gameId, GameUpdateRequest request) {
        DocumentReference ref = doc(userId, gameId);
        DocumentSnapshot snap = await(ref.get());
        if (!snap.exists()) {
            throw new GameNotFoundException(gameId);
        }
        GameItem item = snap.toObject(GameItem.class);
        item.applyUpdate(request, Instant.now().toString());
        await(ref.set(item));
    }

    @Override
    public List<GameHistoryEntry> findHistory(String userId, int limit) {
        QuerySnapshot qs = await(firestore.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .whereIn("status", List.of(GameStatus.SOLVED.getValue(), GameStatus.ABANDONED.getValue()))
                .orderBy("endedAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get());
        return qs.getDocuments().stream()
                .map(d -> d.toObject(GameItem.class))
                .map(item -> new GameHistoryEntry(
                        item.getGameId(),
                        item.getDifficulty(),
                        GameStatus.SOLVED.getValue().equals(item.getStatus()) ? "won" : "abandoned",
                        item.getEndedAt(),
                        item.getTimeSpentSeconds(),
                        item.getHintsUsed(),
                        item.getScore()))
                .toList();
    }

    @Override
    public void abandonGame(String userId, String gameId) {
        DocumentReference ref = doc(userId, gameId);
        DocumentSnapshot snap = await(ref.get());
        if (!snap.exists()) {
            return;
        }
        GameItem item = snap.toObject(GameItem.class);
        item.markAbandoned(Instant.now().toString());
        await(ref.set(item));
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
