package com.sudoku.puzzle.web;

import com.sudoku.puzzle.SudokuService;
import com.sudoku.puzzle.hint.HintResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST entry point for stateless Sudoku puzzle operations.
 *
 * <p>Exposes puzzle generation, board validation, logical hint retrieval, and candidate
 * calculation to unauthenticated callers. No game state is created or modified here —
 * all endpoints are pure functions over the submitted grid, keeping the puzzle logic
 * accessible without requiring a player account.
 */
@Path("/puzzles")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PuzzleResource {

    @Inject
    SudokuService sudokuService;

    @GET
    @Path("/generate")
    public PuzzleResponse generate(@QueryParam("difficulty") @DefaultValue("medium") String difficulty) {
        return sudokuService.generatePuzzle(difficulty);
    }

    @POST
    @Path("/validate")
    public ValidationResponse validate(BoardRequest request) {
        return sudokuService.validatePuzzle(request);
    }

    @POST
    @Path("/hint")
    public Response hint(BoardRequest request) {
        // @spec HE-BE-007
        return switch (sudokuService.getHint(request)) {
            case HintResult.Found f         -> Response.ok(f.hint()).build();
            case HintResult.PuzzleSolved ignored -> Response.noContent().build();
            case HintResult.NoStrategyApplied ignored -> Response.status(Response.Status.NOT_FOUND).build();
        };
    }

    @POST
    @Path("/candidates")
    public CandidatesResponse candidates(BoardRequest request) {
        return sudokuService.getCandidates(request);
    }
}
