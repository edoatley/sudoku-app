package com.sudoku.puzzle.developer;

import com.sudoku.dto.PuzzleResponse;
import com.sudoku.puzzle.hint.HintStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Developer-only endpoints for testing hint strategies in the UI.
 * All paths are under /dev and should only be exposed in non-production deployments.
 */
@ApplicationScoped
@Path("/dev")
@Produces(MediaType.APPLICATION_JSON)
public class DevResource {

    private final Map<String, HintStrategy> strategyBySlug;

    @Inject
    public DevResource(Instance<HintStrategy> strategyInstance) {
        this.strategyBySlug = strategyInstance.stream()
                .collect(Collectors.toMap(HintStrategy::getSlug, s -> s));
    }

    /**
     * Returns a puzzle grid designed to exercise the named hint technique.
     *
     * <p>The grid is pre-baked: it is already in the state where the target technique
     * is immediately applicable. The hint endpoint's {@code minRank} parameter ensures
     * simpler strategies are skipped at hint-click time.
     *
     * @param technique the markdown slug of the target strategy (e.g. "naked-pair")
     * @return 200 with a {@link PuzzleResponse}, or 404 if the slug is unknown
     */
    @GET
    @Path("/hint-demo")
    public Response hintDemo(@QueryParam("technique") String technique) {
        if (technique == null || technique.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"technique query parameter is required\"}")
                    .build();
        }

        List<List<Integer>> grid = HintDemoGrids.forSlug(technique);
        if (grid == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Unknown technique: " + technique + "\"}")
                    .build();
        }

        HintStrategy strategy = strategyBySlug.get(technique);
        int targetRank = strategy != null ? strategy.getDifficultyRank() : Integer.MAX_VALUE;
        PuzzleResponse puzzleResponse = new PuzzleResponse(grid, null, "demo", targetRank);
        return Response.ok(puzzleResponse).build();
    }
}
