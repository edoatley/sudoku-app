package com.sudoku.leaderboard.persistence;

import com.sudoku.leaderboard.LeaderboardRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Selects the active {@link LeaderboardRepository} implementation at runtime based on
 * the {@code sudoku.persistence} property. The Firestore-side leaderboard is a no-op
 * (leaderboards are not part of the GCP slice). See {@code GameRepositoryProducer} for
 * the {@code @Typed}/{@code Instance} rationale.
 */
@ApplicationScoped
public class LeaderboardRepositoryProducer {

    @Inject
    Instance<NoOpLeaderboardRepository> firestore;

    @Inject
    Instance<DynamoDbLeaderboardRepository> dynamoDb;

    @Produces
    @ApplicationScoped
    public LeaderboardRepository leaderboardRepository() {
        return firestore.isResolvable() ? firestore.get() : dynamoDb.get();
    }
}
