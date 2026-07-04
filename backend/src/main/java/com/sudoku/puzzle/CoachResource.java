package com.sudoku.puzzle;

import com.sudoku.dto.CoachRequest;
import com.sudoku.dto.CoachResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

// @spec SC-API-001, SC-API-002, SC-API-003, SC-API-004, SC-API-010, SC-API-011, SC-API-012

/**
 * REST entry point for the AI coaching endpoint.
 *
 * <p>Requires a valid Cognito JWT, enforced by API Gateway before Lambda invocation.
 * All puzzle logic is stateless — the board state and conversation history are supplied
 * in each request body.
 */
@Path("/puzzles/coach")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CoachResource {

    @Inject
    SudokuCoachService coachService;

    @POST
    public Response coach(CoachRequest request) {
        if (request == null || request.board() == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (request.userMessage() == null || request.userMessage().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        return switch (coachService.coach(request)) {
            case SudokuCoachService.CoachResult.Response r -> Response.ok(r.coachResponse()).build();
            case SudokuCoachService.CoachResult.PuzzleSolved ignored -> Response.noContent().build();
        };
    }
}
