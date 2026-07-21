package com.sudoku.game.web;

import com.sudoku.auth.UserIdentityResolver;
import com.sudoku.game.GameService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;

import java.util.Map;

/**
 * REST entry point for managing a player's Sudoku game sessions.
 *
 * <p>Authenticated callers can create new games, resume an in-progress game, load any
 * saved game by ID, update game progress, and import puzzles captured via the
 * image-recognition pipeline. User identity is sourced from the validated JWT (Cognito on
 * AWS, Identity Platform on GCP), or the mock identity injected by {@code DevIdentityAugmentor}
 * in dev/it/test.
 */
@Path("/games")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GameResource {

    @Inject
    GameService gameService;

    @Inject
    UserIdentityResolver userIdentityResolver;

    @POST
    public Response createGame(Map<String, String> body) {
        String userId = userIdentityResolver.resolveUserId();
        String difficulty = body.getOrDefault("difficulty", "medium");
        GameState gameState = gameService.createGame(userId, difficulty);
        return Response.status(Response.Status.CREATED).entity(gameState).build();
    }

    @POST
    @Path("/from-image")
    public Response createGameFromExistingGrid(CreateGameFromGridRequest body) {
        String userId = userIdentityResolver.resolveUserId();
        GameState gameState = gameService.createGameFromExistingGrid(userId, body.originalGrid());
        return Response.status(Response.Status.CREATED).entity(gameState).build();
    }

    @GET
    @Path("/current")
    public Response getCurrentGame() {
        String userId = userIdentityResolver.resolveUserId();
        Optional<GameState> game = gameService.findInProgress(userId);
        return game.map(g -> Response.ok(g).build())
                   .orElse(Response.noContent().build());
    }

    @GET
    @Path("/{gameId}")
    public GameState loadGame(@PathParam("gameId") String gameId) {
        String userId = userIdentityResolver.resolveUserId();
        return gameService.loadGame(userId, gameId);
    }

    @PATCH
    @Path("/{gameId}")
    public Response updateGame(@PathParam("gameId") String gameId, GameUpdateRequest request) {
        String userId = userIdentityResolver.resolveUserId();
        gameService.updateGame(userId, gameId, request);
        return Response.ok().build();
    }

    // @spec GH-API-001, GH-API-002, GH-API-003
    @GET
    @Path("/history")
    public GameHistoryResponse getGameHistory(
            @QueryParam("limit") @DefaultValue("20") int limit) {
        String userId = userIdentityResolver.resolveUserId();
        return gameService.getGameHistory(userId, Math.min(limit, 100)); // @spec GH-BE-003
    }
}
