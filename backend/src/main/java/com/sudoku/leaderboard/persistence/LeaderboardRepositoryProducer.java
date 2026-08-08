package com.sudoku.leaderboard.persistence;

import com.sudoku.leaderboard.LeaderboardRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Selects the active {@link LeaderboardRepository} implementation at runtime based on
 * the {@code sudoku.persistence} property: {@link FirestoreLeaderboardRepository} on GCP,
 * {@link DynamoDbLeaderboardRepository} on AWS. See {@code GameRepositoryProducer} for
 * the {@code @Typed}/{@code Instance} rationale.
 */
@ApplicationScoped
public class LeaderboardRepositoryProducer {

    @Inject
    Instance<FirestoreLeaderboardRepository> firestore;

    @Inject
    Instance<DynamoDbLeaderboardRepository> dynamoDb;

    @Produces
    @ApplicationScoped
    public LeaderboardRepository leaderboardRepository() {
        return firestore.isResolvable() ? firestore.get() : dynamoDb.get();
    }
}
