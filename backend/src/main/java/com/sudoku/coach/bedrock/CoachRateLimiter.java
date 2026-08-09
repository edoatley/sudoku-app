package com.sudoku.coach.bedrock;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Per-user, per-minute rate limit for the AI coach endpoint. The active implementation is selected
 * at runtime by {@code sudoku.persistence} via {@code CoachRateLimiterProducer}:
 * {@link DynamoDbCoachRateLimiter} on AWS, {@link FirestoreCoachRateLimiter} on GCP.
 *
 * @spec SC-RL-003, SC-RL-004, SC-RL-011
 */
public interface CoachRateLimiter {

    /**
     * Attempts to consume a rate-limit slot for the given user in the current UTC minute. Returns
     * true if the call is allowed, false if the per-minute limit has been reached. Implementations
     * fail open (return true) on any storage error, so infrastructure issues never block the user.
     */
    boolean tryConsume(String userId);

    /**
     * Returns the number of seconds until the start of the next UTC minute — cloud-neutral time
     * arithmetic shared by both adapters.
     */
    default int secondsUntilNextWindow() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime nextMinute = now.withSecond(0).withNano(0).plusMinutes(1);
        return (int) (nextMinute.toEpochSecond() - Instant.now().getEpochSecond());
    }
}
