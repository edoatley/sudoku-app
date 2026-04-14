package com.sudoku.game;

import com.sudoku.dto.CreateGameFromGridRequest;
import com.sudoku.dto.GameState;
import com.sudoku.dto.GameUpdateRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Map;

/**
 * REST entry point for managing a player's Sudoku game sessions.
 *
 * <p>Authenticated callers can create new games, resume an in-progress game, load any
 * saved game by ID, update game progress, and import puzzles captured via the
 * image-recognition pipeline. User identity is sourced from the JWT principal injected
 * by {@code DevUserFilter} in development or by API Gateway in production.
 */
@Path("/games")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GameResource {

    @Inject
    GameService gameService;

    @Context
    SecurityContext securityContext;

    @POST
    public Response createGame(Map<String, String> body) {
        String userId = securityContext.getUserPrincipal().getName();
        String difficulty = body.getOrDefault("difficulty", "medium");
        GameState gameState = gameService.createGame(userId, difficulty);
        return Response.status(Response.Status.CREATED).entity(gameState).build();
    }

    @POST
    @Path("/from-image")
    public Response createGameFromExistingGrid(CreateGameFromGridRequest body) {
        String userId = securityContext.getUserPrincipal().getName();
        GameState gameState = gameService.createGameFromExistingGrid(userId, body.originalGrid());
        return Response.status(Response.Status.CREATED).entity(gameState).build();
    }

    @GET
    @Path("/current")
    public Response getCurrentGame() {
        String userId = securityContext.getUserPrincipal().getName();
        Optional<GameState> game = gameService.findInProgress(userId);
        return game.map(g -> Response.ok(g).build())
                   .orElse(Response.noContent().build());
    }

    @GET
    @Path("/{gameId}")
    public GameState loadGame(@PathParam("gameId") String gameId) {
        String userId = securityContext.getUserPrincipal().getName();
        return gameService.loadGame(userId, gameId);
    }

    @PATCH
    @Path("/{gameId}")
    public Response updateGame(@PathParam("gameId") String gameId, GameUpdateRequest request) {
        String userId = securityContext.getUserPrincipal().getName();
        gameService.updateGame(userId, gameId, request);
        return Response.ok().build();
    }
}
