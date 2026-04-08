package com.sudoku.auth;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-side email allowlist filter.
 *
 * <p>When {@code app.allowed.emails} is non-empty, any authenticated request whose JWT
 * {@code email} claim is not in the list is rejected with 403 Forbidden.
 *
 * <p>Setting {@code app.allowed.emails=} (empty string, the default) disables the check,
 * which is the behaviour used in dev/test profiles.
 */
@Provider
public class AllowedUsersFilter implements ContainerRequestFilter {

    @Inject
    SecurityIdentity identity;

    @ConfigProperty(name = "app.allowed.emails")
    Optional<String> allowedEmailsRaw;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String raw = allowedEmailsRaw.orElse("");
        if (raw.isBlank()) {
            return; // allowlist disabled (dev/test)
        }

        if (identity.isAnonymous()) {
            return; // no principal — public route or dev filter not yet run
        }

        Set<String> allowed = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        Object emailClaim = identity.getAttribute("email");
        String email = emailClaim != null ? emailClaim.toString() : null;

        if (email == null || !allowed.contains(email)) {
            ctx.abortWith(
                Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"Access denied\"}")
                    .build()
            );
        }
    }
}
