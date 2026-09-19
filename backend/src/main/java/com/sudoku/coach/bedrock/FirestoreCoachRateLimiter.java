package com.sudoku.coach.bedrock;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Firestore-backed coach rate limiter (GCP adapter). Mirrors {@link DynamoDbCoachRateLimiter}: a
 * per-user/per-minute counter keyed {@code userId__window} in the {@code coachRateLimits}
 * collection. Firestore has no atomic conditional increment, so the read-check-write runs in a
 * transaction; the {@code expiresAt} field is a Timestamp so the collection's TTL policy (CP-GCP-022)
 * purges stale windows. Fails open on any Firestore error, matching the AWS adapter.
 *
 * <p>Selected at runtime via {@code sudoku.persistence=firestore}.
 *
 * @spec SC-RL-003, SC-RL-004, SC-RL-011
 */
@ApplicationScoped
@Typed(FirestoreCoachRateLimiter.class)
@LookupIfProperty(name = "sudoku.persistence", stringValue = "firestore")
public class FirestoreCoachRateLimiter implements CoachRateLimiter {

    private static final org.jboss.logging.Logger LOG = org.jboss.logging.Logger.getLogger(FirestoreCoachRateLimiter.class);
    private static final DateTimeFormatter WINDOW_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    static final String COLLECTION = "coachRateLimits";

    @Inject
    Firestore firestore;

    @ConfigProperty(name = "coach.rate-limit.per-minute")
    int perMinuteLimit;

    /**
     * The clock the window is read from. Production uses system UTC; tests pin it.
     *
     * <p>The counter is keyed per UTC minute and the window was previously read from the wall
     * clock on every call, so a test whose calls straddled a boundary saw the count reset
     * mid-assertion and failed intermittently. Injecting the clock removes that race without
     * widening the window this limiter enforces.
     */
    Clock clock = Clock.systemUTC();

    @Override
    public boolean tryConsume(String userId) {
        // Rendered in UTC regardless of the clock's own zone, so the window stays the UTC minute
        // SC-RL-003 specifies even if a zoned clock is injected.
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        String window = now.format(WINDOW_FMT);
        // TTL = 2 minutes after the start of the current minute window (Timestamp for the TTL policy)
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(
                now.withSecond(0).withNano(0).plusMinutes(2).toEpochSecond(), 0);
        DocumentReference ref = firestore.collection(COLLECTION).document(userId + "__" + window);

        try {
            return await(firestore.runTransaction(tx -> {
                DocumentSnapshot snap = tx.get(ref).get();
                long callCount = snap.contains("callCount") ? snap.getLong("callCount") : 0L;
                if (callCount >= perMinuteLimit) {
                    return false;
                }
                tx.set(ref, Map.of("callCount", callCount + 1, "expiresAt", expiresAt));
                return true;
            }));
        } catch (Exception e) {
            LOG.warnf("Rate limit check failed for userId=%s, failing open: %s", userId, e.getMessage());
            return true;
        }
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
