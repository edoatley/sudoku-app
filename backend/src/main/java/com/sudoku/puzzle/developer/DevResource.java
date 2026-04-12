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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Developer-only endpoints for testing hint strategies in the UI.
 * All paths are under /dev and should only be exposed in non-production deployments.
 */
@ApplicationScoped
@Path("/dev")
@Produces(MediaType.APPLICATION_JSON)
public class DevResource {

    private final List<HintStrategy> strategies;

    @Inject
    public DevResource(Instance<HintStrategy> strategyInstance) {
        this.strategies = strategyInstance.stream()
                .sorted(Comparator.comparingInt(HintStrategy::getDifficultyRank))
                .collect(Collectors.toList());
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

        int targetRank = rankForSlug(technique);
        PuzzleResponse puzzleResponse = new PuzzleResponse(grid, null, "demo", targetRank);
        return Response.ok(puzzleResponse).build();
    }

    /**
     * Resolves the difficulty rank of the target strategy by its slug.
     * Returns Integer.MAX_VALUE if the slug is not found.
     */
    private int rankForSlug(String slug) {
        return strategies.stream()
                .filter(s -> slugMatchesStrategy(slug, s))
                .mapToInt(HintStrategy::getDifficultyRank)
                .findFirst()
                .orElse(Integer.MAX_VALUE);
    }

    /**
     * Maps a slug to a strategy by simple class-name convention.
     * e.g. "naked-pair" → NakedPairStrategy, "full-house" → FullHouseStrategy.
     */
    private boolean slugMatchesStrategy(String slug, HintStrategy strategy) {
        String simpleName = strategy.getClass().getSimpleName().toLowerCase();
        String normalised = slug.replace("-", "");
        return simpleName.startsWith(normalised);
    }
}
