package com.sudoku.game;

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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/games")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GameResource {

    @Inject
    GameService gameService;

    @POST
    public Response createGame(Map<String, String> body) {
        String difficulty = body.getOrDefault("difficulty", "medium");
        GameState gameState = gameService.createGame(difficulty);
        return Response.status(Response.Status.CREATED).entity(gameState).build();
    }

    @GET
    @Path("/{gameId}")
    public GameState loadGame(@PathParam("gameId") String gameId) {
        return gameService.loadGame(gameId);
    }

    @PATCH
    @Path("/{gameId}")
    public Response updateGame(@PathParam("gameId") String gameId, GameUpdateRequest request) {
        gameService.updateGame(gameId, request);
        return Response.ok().build();
    }
}
