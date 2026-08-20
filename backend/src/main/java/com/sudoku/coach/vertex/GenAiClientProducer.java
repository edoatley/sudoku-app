package com.sudoku.coach.vertex;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

/**
 * Produces the {@link Client} used by {@link VertexCoachClient}. No Quarkiverse extension exists
 * for {@code com.google.genai} yet, so this authenticates the same way the rest of the app's
 * google-cloud integrations do: Application Default Credentials (the Cloud Run runtime service
 * account), no long-lived keys.
 *
 * @spec SC-GCP-002, CP-GCP-090
 */
@ApplicationScoped
public class GenAiClientProducer {

    // Optional: blank/unset (local dev without GCP_PROJECT_ID) lets the SDK auto-detect the
    // project from ADC/the metadata server, same as the old quarkiverse extension did implicitly.
    @ConfigProperty(name = "coach.vertex.project-id")
    Optional<String> projectId;

    @ConfigProperty(name = "coach.vertex.location")
    String location;

    // @Dependent (the default — no scope annotation), not @ApplicationScoped: Client is a final
    // class, so ArC can't generate a normal-scope client proxy for it. @Dependent beans are
    // injected directly, no proxy required. VertexCoachClient (the sole injection point) is
    // itself @ApplicationScoped, so this is still only ever constructed once in practice.
    @Produces
    public Client genAiClient() {
        try {
            Client.Builder builder = Client.builder()
                    .vertexAI(true)
                    .location(location)
                    .credentials(GoogleCredentials.getApplicationDefault());
            projectId.ifPresent(builder::project);
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to resolve Application Default Credentials for Vertex AI", e);
        }
    }
}
