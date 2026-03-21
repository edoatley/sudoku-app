package com.sudoku.auth;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

import java.security.Principal;

/**
 * Dev/IT-profile-only filter that injects a mock SecurityContext when no Authorization header
 * is present. This allows quarkus:dev and integration tests to exercise authenticated endpoints
 * (games/*, players/*) without a real Cognito pool.
 *
 * Not included in the production Lambda bundle — @IfBuildProfile removes it at compile time.
 */
@Provider
@IfBuildProfile(anyOf = {"dev", "it"})
public class DevUserFilter implements ContainerRequestFilter {

    private static final String MOCK_USER_ID = "local-dev-user";

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (ctx.getHeaderString("Authorization") == null) {
            ctx.setSecurityContext(new jakarta.ws.rs.core.SecurityContext() {
                @Override
                public Principal getUserPrincipal() {
                    return () -> MOCK_USER_ID;
                }

                @Override
                public boolean isUserInRole(String role) {
                    return false;
                }

                @Override
                public boolean isSecure() {
                    return false;
                }

                @Override
                public String getAuthenticationScheme() {
                    return "MOCK";
                }
            });
        }
    }
}
