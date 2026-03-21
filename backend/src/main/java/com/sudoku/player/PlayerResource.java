package com.sudoku.player;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

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
        Object value = identity.getAttribute(claimName);
        return value != null ? value.toString() : null;
    }
}
