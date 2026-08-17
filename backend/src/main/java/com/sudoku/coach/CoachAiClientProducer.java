package com.sudoku.coach;

import com.sudoku.coach.bedrock.BedrockCoachClient;
import com.sudoku.coach.vertex.VertexCoachClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * Selects the active {@link CoachAiClient} at runtime based on the {@code coach.ai.provider}
 * property: {@code VertexCoachClient} (Gemini) when {@code vertex} (set via
 * {@code %gcp.coach.ai.provider=vertex}), else {@code BedrockCoachClient} (the {@code bedrock}
 * default). Mirrors {@code GameRepositoryProducer} / {@code CoachRateLimiterProducer}: the adapters
 * are {@code @Typed} to their concrete class and gated by {@code @LookupIf/UnlessProperty}, so only
 * the resolvable adapter is {@code get()}'d and only that provider's SDK client is instantiated.
 *
 * @spec SC-GCP-001
 */
@ApplicationScoped
public class CoachAiClientProducer {

    @Inject
    Instance<VertexCoachClient> vertex;

    @Inject
    Instance<BedrockCoachClient> bedrock;

    @Produces
    @ApplicationScoped
    public CoachAiClient coachAiClient() {
        return vertex.isResolvable() ? vertex.get() : bedrock.get();
    }
}
