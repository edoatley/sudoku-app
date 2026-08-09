package com.sudoku.coach.bedrock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Selects the active {@link CoachRateLimiter} implementation at runtime based on the
 * {@code sudoku.persistence} property: {@link FirestoreCoachRateLimiter} on GCP,
 * {@link DynamoDbCoachRateLimiter} on AWS. See {@code GameRepositoryProducer} for the
 * {@code @Typed}/{@code Instance} rationale.
 *
 * @spec SC-RL-003, SC-RL-004, SC-RL-011
 */
@ApplicationScoped
public class CoachRateLimiterProducer {

    @Inject
    Instance<FirestoreCoachRateLimiter> firestore;

    @Inject
    Instance<DynamoDbCoachRateLimiter> dynamoDb;

    @Produces
    @ApplicationScoped
    public CoachRateLimiter coachRateLimiter() {
        return firestore.isResolvable() ? firestore.get() : dynamoDb.get();
    }
}
