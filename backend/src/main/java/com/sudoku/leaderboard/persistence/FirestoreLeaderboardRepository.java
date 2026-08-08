package com.sudoku.leaderboard.persistence;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.sudoku.leaderboard.LeaderboardRepository;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Firestore-backed leaderboard aggregate (GCP adapter).
 *
 * <p>Selected at runtime via {@code sudoku.persistence=firestore}, replacing the former no-op stub.
 * Reuses {@link LeaderboardItem} as the document model (Firestore's POJO mapper serialises its
 * getters; the DynamoDB annotations are inert), so one aggregate item lives in the
 * {@code leaderboard} collection keyed by {@code userId}. Mirrors
 * {@code DynamoDbLeaderboardRepository} behaviour; the read-modify-write is wrapped in a Firestore
 * transaction because Firestore has no single-operation equivalent of DynamoDB's atomic ADD +
 * conditional-min.
 *
 * @spec LT-GCP-002, LT-GCP-003, LT-GCP-004, LT-GCP-005, LT-GCP-006, LT-GCP-007, LT-GCP-008
 */
@ApplicationScoped
@Typed(FirestoreLeaderboardRepository.class)
@LookupIfProperty(name = "sudoku.persistence", stringValue = "firestore")
public class FirestoreLeaderboardRepository implements LeaderboardRepository {

    static final String COLLECTION = "leaderboard";

    @Inject
    Firestore firestore;

    // @spec LT-GCP-003, LT-GCP-004, LT-GCP-005, LT-GCP-006, LT-GCP-007
    @Override
    public void updateOnSolve(String userId, String difficulty, int elapsedSeconds, int score, String outcome) {
        boolean won = "won".equals(outcome);
        String now = Instant.now().toString();
        DocumentReference ref = firestore.collection(COLLECTION).document(userId);

        await(firestore.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref).get();
            LeaderboardItem item = snap.exists() ? snap.toObject(LeaderboardItem.class) : newItem(userId);

            item.setTotalGames(item.getTotalGames() + 1);
            if (won) {
                item.setTotalWins(item.getTotalWins() + 1);
                item.setTotalScore(item.getTotalScore() + score);
                item.setTotalElapsedSeconds(item.getTotalElapsedSeconds() + elapsedSeconds);
                updateBestTime(item, difficulty, elapsedSeconds);
            }
            item.setUpdatedAt(now);
            tx.set(ref, item);
            return null;
        }));
    }

    // @spec LT-GCP-008
    @Override
    public List<LeaderboardItem> findAll() {
        return await(firestore.collection(COLLECTION).get()).getDocuments().stream()
                .map(d -> d.toObject(LeaderboardItem.class))
                .toList();
    }

    // @spec LT-GCP-004: absent document starts from a zeroed item carrying the userId (the join key).
    private static LeaderboardItem newItem(String userId) {
        LeaderboardItem item = new LeaderboardItem();
        item.setUserId(userId);
        return item;
    }

    // @spec LT-GCP-007: best time is the minimum; unknown difficulty maps to medium.
    private static void updateBestTime(LeaderboardItem item, String difficulty, int elapsedSeconds) {
        switch (difficulty) {
            case "easy" -> item.setBestEasySeconds(min(item.getBestEasySeconds(), elapsedSeconds));
            case "hard" -> item.setBestHardSeconds(min(item.getBestHardSeconds(), elapsedSeconds));
            case "imported" -> item.setBestImportedSeconds(min(item.getBestImportedSeconds(), elapsedSeconds));
            default -> item.setBestMediumSeconds(min(item.getBestMediumSeconds(), elapsedSeconds));
        }
    }

    private static Integer min(Integer current, int candidate) {
        return (current == null || candidate < current) ? candidate : current;
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
