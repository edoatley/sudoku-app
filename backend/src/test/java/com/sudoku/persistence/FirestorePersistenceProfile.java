package com.sudoku.persistence;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Builds the application with the GCP persistence backend fully selected
 * ({@code sudoku.persistence=firestore}) and the Firestore Dev Services emulator running.
 *
 * <p>Unlike {@code FirestoreEmulatorProfile} (which only starts the emulator and reflectively
 * injects the adapter under test), this profile sets the runtime property so the persistence
 * producers resolve the real GCP bean graph: Firestore game/player adapters + no-op leaderboard,
 * with the DynamoDB adapters left unresolvable. It exists to prove the whole CDI container boots on
 * the firestore backend.
 */
public class FirestorePersistenceProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "sudoku.persistence", "firestore",
                "quarkus.google.cloud.firestore.devservice.enabled", "true",
                "quarkus.google.cloud.firestore.use-emulator-credentials", "true",
                "quarkus.google.cloud.project-id", "sudoku-test");
    }
}
