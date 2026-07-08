package com.sudoku.admin;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Server-side admin group check for {@code @AdminOnly}-annotated resources.
 *
 * <p>Restricts access to members of the {@code app.admin.group} Cognito group (default
 * {@code administrators}), read from the JWT {@code cognito:groups} claim.
 *
 * <p>Anonymous requests are allowed through — in dev/it/test profiles OIDC is disabled and
 * every request is anonymous, so the local data browser must keep working without a token.
 * In production, {@code /admin/*} routes are JWT-protected at API Gateway, so an anonymous
 * request never reaches the Lambda there.
 */
@Provider
@AdminOnly
public class AdminAuthorizationFilter implements ContainerRequestFilter {

    @Inject
    SecurityIdentity identity;

    @ConfigProperty(name = "app.admin.group", defaultValue = "administrators")
    String adminGroup;

    /**
     * Filters the incoming request based on Cognito group membership.
     *
     * @param ctx the container request context
     */
    @Override
    public void filter(ContainerRequestContext ctx) {

        // Anonymous identities (dev/it/test, OIDC disabled) skip the check.
        if (identity.isAnonymous()) {
            return;
        }

        if (!extractGroups().contains(adminGroup)) {
            ctx.abortWith(
                Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"Access denied\"}")
                    .build()
            );
        }
    }

    /**
     * Extracts the cognito:groups claim as a set of group names, from the JWT principal or
     * (as a fallback) the identity attributes. Tolerates the shapes Cognito/Quarkus OIDC may
     * expose the claim as: a JSON array, a plain Collection, or a String array.
     *
     * @return the set of group names, empty if the claim is absent
     */
    private Set<String> extractGroups() {
        Object claim = null;

        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            claim = jwt.getClaim("cognito:groups");
        }

        if (claim == null) {
            claim = identity.getAttribute("cognito:groups");
        }

        Set<String> groups = new HashSet<>();
        if (claim instanceof JsonArray jsonArray) {
            for (JsonValue value : jsonArray) {
                groups.add(value instanceof JsonString jsonString ? jsonString.getString() : value.toString());
            }
        } else if (claim instanceof Collection<?> collection) {
            for (Object element : collection) {
                if (element != null) groups.add(element.toString());
            }
        } else if (claim instanceof String[] array) {
            for (String element : array) {
                if (element != null) groups.add(element);
            }
        }
        return groups;
    }
}
