package com.sudoku.admin.persistence;

import com.sudoku.admin.AdminDataRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Selects the active {@link AdminDataRepository} implementation at runtime based on the
 * {@code sudoku.persistence} property: {@link FirestoreAdminDataRepository} on GCP,
 * {@link DynamoDbAdminDataRepository} on AWS. See {@code GameRepositoryProducer} for the
 * {@code @Typed}/{@code Instance} rationale.
 *
 * @spec UM-GCP-010
 */
@ApplicationScoped
public class AdminDataRepositoryProducer {

    @Inject
    Instance<FirestoreAdminDataRepository> firestore;

    @Inject
    Instance<DynamoDbAdminDataRepository> dynamoDb;

    @Produces
    @ApplicationScoped
    public AdminDataRepository adminDataRepository() {
        return firestore.isResolvable() ? firestore.get() : dynamoDb.get();
    }
}
