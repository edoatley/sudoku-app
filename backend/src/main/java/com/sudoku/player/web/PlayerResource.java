package com.sudoku.player.web;

import com.sudoku.player.PlayerService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * REST entry point for player profile management.
 *
 * <p>Exposes GET /players/me for lazy profile creation on first access, and
 * PATCH /players/me for updating display name and avatar. JWT claims are read from
 * the Quarkus {@code SecurityIdentity} to seed new profiles with the player's
 * real Cognito identity without a separate registration step.
 */
@Path("/players")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class PlayerResource {

    @Inject
    PlayerService playerService;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/me")
    public PlayerProfile getMe(@Context SecurityContext sc) {
        String userId      = sc.getUserPrincipal().getName();
        String email       = getClaimAsString("email");
        String displayName = getClaimAsString("name");
        return playerService.getOrCreateProfile(userId, email, displayName);
    }

    // @spec UM-API-002, UM-BE-003, UM-BE-004, UM-BE-005, UM-BE-006
    @PATCH
    @Path("/me")
    @Consumes(MediaType.APPLICATION_JSON)
    public PlayerProfile updateMe(@Context SecurityContext sc, PlayerUpdateRequest request) {
        String userId = sc.getUserPrincipal().getName();
        return playerService.updateProfile(userId, request);
    }

    private String getClaimAsString(String claimName) {
        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            String value = jwt.getClaim(claimName);
            if (value != null) return value;
        }
        Object attr = identity.getAttribute(claimName);
        return attr != null ? attr.toString() : null;
    }
}
