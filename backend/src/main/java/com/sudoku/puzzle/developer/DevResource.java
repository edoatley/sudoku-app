package com.sudoku.puzzle.developer;

import com.sudoku.domain.Board;
import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.HintResponse;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
     * <p>The returned grid has all cells solvable by simpler strategies pre-filled,
     * so the first hint click in the UI will fire the named technique.
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

        List<List<Integer>> baseGrid = HintDemoGrids.forSlug(technique);
        if (baseGrid == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Unknown technique: " + technique + "\"}")
                    .build();
        }

        int targetRank = rankForSlug(technique);
        List<List<Integer>> readyGrid = autocompleteSimpler(baseGrid, targetRank);

        PuzzleResponse puzzleResponse = new PuzzleResponse(readyGrid, "demo");
        return Response.ok(puzzleResponse).build();
    }

    /**
     * Resolves the difficulty rank of the target strategy by its slug.
     * Returns Integer.MAX_VALUE if the slug is not found (no autocomplete).
     */
    private int rankForSlug(String slug) {
        return strategies.stream()
                .filter(s -> {
                    // We can't call markdownSlug() on the interface, but we can match by
                    // evaluating a dummy board — instead, we resolve rank via HintDemoGrids
                    // slug→rank mapping derived from the strategy's own difficulty rank.
                    // Since we can't access markdownSlug() here, we rely on a lookup table.
                    return slugMatchesStrategy(slug, s);
                })
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
        // "naked-pair" → "nakedpair", "NakedPairStrategy" → "nakedpairstrategy"
        String normalised = slug.replace("-", "");
        return simpleName.startsWith(normalised);
    }

    /**
     * Applies all strategies with rank {@literal <} targetRank repeatedly until none fire,
     * filling in any solved cells. This ensures the grid is ready for the target technique.
     */
    private List<List<Integer>> autocompleteSimpler(List<List<Integer>> grid, int targetRank) {
        // Work on a mutable copy
        int[][] work = to2dArray(grid);

        List<HintStrategy> simpler = strategies.stream()
                .filter(s -> s.getDifficultyRank() < targetRank)
                .toList();

        if (simpler.isEmpty()) return grid;

        boolean progress = true;
        while (progress) {
            progress = false;
            List<List<Integer>> current = toImmutableList(work);
            Board board = Board.fromGrid(current);
            board.calculateAllCandidates();

            for (HintStrategy strategy : simpler) {
                Optional<HintResponse> hint = strategy.evaluate(board);
                if (hint.isPresent()) {
                    List<ActionableCell> solved = hint.get().solvedCells();
                    if (solved != null && !solved.isEmpty()) {
                        for (ActionableCell cell : solved) {
                            work[cell.row()][cell.col()] = cell.value();
                        }
                        progress = true;
                        break; // restart from the beginning with fresh candidates
                    }
                }
            }
        }

        return toImmutableList(work);
    }

    private int[][] to2dArray(List<List<Integer>> grid) {
        int[][] arr = new int[9][9];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                arr[r][c] = grid.get(r).get(c);
            }
        }
        return arr;
    }

    private List<List<Integer>> toImmutableList(int[][] arr) {
        List<List<Integer>> result = new ArrayList<>(9);
        for (int r = 0; r < 9; r++) {
            List<Integer> row = new ArrayList<>(9);
            for (int c = 0; c < 9; c++) {
                row.add(arr[r][c]);
            }
            result.add(List.copyOf(row));
        }
        return List.copyOf(result);
    }
}
