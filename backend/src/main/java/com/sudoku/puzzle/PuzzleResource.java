package com.sudoku.puzzle;

import com.sudoku.dto.BoardRequest;
import com.sudoku.dto.CandidatesResponse;
import com.sudoku.dto.PuzzleResponse;
import com.sudoku.dto.ValidationResponse;
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
        return sudokuService.getHint(request)
                .map(hint -> Response.ok(hint).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Path("/candidates")
    public CandidatesResponse candidates(BoardRequest request) {
        return sudokuService.getCandidates(request);
    }
}
