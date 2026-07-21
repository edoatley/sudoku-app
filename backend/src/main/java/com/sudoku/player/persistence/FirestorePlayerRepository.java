package com.sudoku.player.persistence;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.sudoku.player.PlayerRepository;
import com.sudoku.player.web.PlayerProfile;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Firestore-backed persistence for player profiles (GCP adapter).
 *
 * <p>Selected at build time via {@code sudoku.persistence=firestore}. Reuses {@link PlayerItem}
 * as the document model (Firestore's POJO mapper serialises its getters; the DynamoDB annotations
 * are inert), so profile mapping is shared with the AWS adapter. Documents live in the
 * {@code players} collection keyed by {@code userId} (the canonical Google-{@code sub} id).
 *
 * @spec UM-GCP-007
 */
@ApplicationScoped
@Typed(FirestorePlayerRepository.class)
@LookupIfProperty(name = "sudoku.persistence", stringValue = "firestore")
public class FirestorePlayerRepository implements PlayerRepository {

    static final String COLLECTION = "players";

    @Inject
    Firestore firestore;

    private DocumentReference doc(String userId) {
        return firestore.collection(COLLECTION).document(userId);
    }

    @Override
    public Optional<PlayerProfile> findById(String userId) {
        DocumentSnapshot snap = await(doc(userId).get());
        return snap.exists()
                ? Optional.of(snap.toObject(PlayerItem.class).toPlayerProfile())
                : Optional.empty();
    }

    @Override
    public PlayerProfile upsert(PlayerProfile profile) {
        await(doc(profile.userId()).set(PlayerItem.from(profile)));
        return profile;
    }

    @Override
    public List<PlayerItem> findAll() {
        return await(firestore.collection(COLLECTION).get()).getDocuments().stream()
                .map(d -> d.toObject(PlayerItem.class))
                .toList();
    }

    // @spec SC-RL-002, SC-RL-005 (GCP path). Non-atomic read-modify-write, matching the AWS
    // adapter's accepted month-boundary race. Unused in the games slice (coach is out of scope).
    @Override
    public void incrementCoachTokens(String userId, long tokensUsed, String currentMonth) {
        if (tokensUsed <= 0) {
            return;
        }
        DocumentReference ref = doc(userId);
        DocumentSnapshot snap = await(ref.get());
        if (!snap.exists()) {
            return;
        }
        PlayerItem item = snap.toObject(PlayerItem.class);
        long base = currentMonth.equals(item.getCoachTokenMonth()) && item.getCoachTokensUsedThisMonth() != null
                ? item.getCoachTokensUsedThisMonth()
                : 0L;
        item.setCoachTokenMonth(currentMonth);
        item.setCoachTokensUsedThisMonth(base + tokensUsed);
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
