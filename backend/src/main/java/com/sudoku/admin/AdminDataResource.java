package com.sudoku.admin;

import com.sudoku.web.DataListResponse;
import com.sudoku.game.web.GameState;
import com.sudoku.player.web.PlayerProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Admin-only read endpoints that expose all table contents for the in-app data browser.
 * Reachable in production by members of the {@code administrators} Cognito group (see
 * {@link AdminAuthorizationFilter}).
 *
 * <p>Data access goes through {@link AdminDataRepository}, whose runtime-selected adapter reads
 * DynamoDB on AWS or Firestore on GCP.
 *
 * @spec UM-GCP-010
 */
@ApplicationScoped
@Path("/admin/data")
@Produces(MediaType.APPLICATION_JSON)
@AdminOnly
public class AdminDataResource {

    @Inject
    AdminDataRepository repository;

    /**
     * Returns all game records as standard JSON (grids as integer arrays, not storage strings).
     *
     * @return all games
     */
    @GET
    @Path("/games")
    public DataListResponse<GameState> listGames() {
        return new DataListResponse<>(repository.findAllGames());
    }

    /**
     * Returns all player profiles.
     *
     * @return all players
     */
    @GET
    @Path("/players")
    public DataListResponse<PlayerProfile> listPlayers() {
        return new DataListResponse<>(repository.findAllPlayers());
    }
}
