package com.sudoku.game.persistence;

import com.sudoku.game.GameRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Selects the active {@link GameRepository} implementation at runtime based on the
 * {@code sudoku.persistence} property. The two adapters are {@code @Typed} to their
 * concrete classes so only this producer exposes the {@link GameRepository} interface,
 * keeping every consumer and test injection point unchanged. Only the resolvable
 * adapter is {@code get()}'d, so the unused cloud SDK's client never initializes.
 */
@ApplicationScoped
public class GameRepositoryProducer {

    @Inject
    Instance<FirestoreGameRepository> firestore;

    @Inject
    Instance<DynamoDbGameRepository> dynamoDb;

    @Produces
    @ApplicationScoped
    public GameRepository gameRepository() {
        return firestore.isResolvable() ? firestore.get() : dynamoDb.get();
    }
}
