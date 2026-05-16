package com.sudoku.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Duration;
import java.time.Instant;

/**
 * Lightweight liveness probe for the Sudoku API.
 *
 * <p>Returns 200 once the Lambda JVM has had enough time to finish warming up,
 * and 503 before that window expires. This lets callers (smoke tests, readiness
 * probes) distinguish "alive but cold" from "ready" — analogous to a Kubernetes
 * readiness probe. The warming window covers worst-case hard-puzzle generation
 * time on a cold JVM (~25 s observed; 30 s gives headroom).
 */
@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    static final Duration WARMING_WINDOW = Duration.ofSeconds(30);

    private final Instant startTime;

    /** CDI no-arg constructor — records startup time for warming-window calculation. */
    public HealthResource() {
        this.startTime = Instant.now();
    }

    /** Package-private constructor for tests that supply a fixed start time. */
    HealthResource(Instant startTime) {
        this.startTime = startTime;
    }

    @GET
    public Response health() {
        boolean warming = Duration.between(startTime, Instant.now()).compareTo(WARMING_WINDOW) < 0;
        HealthResponse body = new HealthResponse(warming ? "warming" : "ok", "sudoku-api");
        return Response.status(warming ? 503 : 200).entity(body).build();
    }

    public record HealthResponse(String status, String service) {}
}
