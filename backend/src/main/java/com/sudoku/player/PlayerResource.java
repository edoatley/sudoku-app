package com.sudoku.player;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * REST entry point for player profile management.
 *
 * <p>Provides a single authenticated endpoint that returns the current player's profile,
 * creating it on first access. JWT claims are read directly from the Quarkus
 * {@code SecurityIdentity} so that the profile is always seeded with the player's
 * real email and display name from Cognito, without requiring a separate registration step.
 */
@Path("/players")
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

    private String getClaimAsString(String claimName) {
        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            String value = jwt.getClaim(claimName);
            if (value != null) return value;
        }
        Object attr = identity.getAttribute(claimName);
        return attr != null ? attr.toString() : null;
    }
}
