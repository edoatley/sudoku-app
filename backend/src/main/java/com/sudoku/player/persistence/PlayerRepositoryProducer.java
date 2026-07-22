package com.sudoku.player.persistence;

import com.sudoku.player.PlayerRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Selects the active {@link PlayerRepository} implementation at runtime based on the
 * {@code sudoku.persistence} property. See {@code GameRepositoryProducer} for the
 * {@code @Typed}/{@code Instance} rationale.
 */
@ApplicationScoped
public class PlayerRepositoryProducer {

    @Inject
    Instance<FirestorePlayerRepository> firestore;

    @Inject
    Instance<DynamoDbPlayerRepository> dynamoDb;

    @Produces
    @ApplicationScoped
    public PlayerRepository playerRepository() {
        return firestore.isResolvable() ? firestore.get() : dynamoDb.get();
    }
}
