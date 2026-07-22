package com.sudoku.game.persistence;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Enables the Firestore Dev Services emulator (Testcontainers) for behaviour tests of the
 * Firestore adapter, without switching the whole build to the firestore persistence profile.
 */
public class FirestoreEmulatorProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.google.cloud.firestore.devservice.enabled", "true",
                "quarkus.google.cloud.firestore.use-emulator-credentials", "true",
                "quarkus.google.cloud.project-id", "sudoku-test");
    }
}
