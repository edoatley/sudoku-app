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
import jakarta.ws.rs.core.SecurityContext;

import java.util.Map;

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
