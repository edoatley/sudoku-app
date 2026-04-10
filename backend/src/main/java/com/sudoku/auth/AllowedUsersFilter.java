package com.sudoku.auth;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

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

    // set of the email addresses configured to use the application
    private final Set<String> allowedEmails;

    @Inject
    public AllowedUsersFilter(@ConfigProperty(name = "app.allowed.emails") Optional<String> allowedEmailsRaw) {
        this.allowedEmails = allowedEmailsRaw.orElse("").isEmpty() ? Set.of() :
                Arrays.stream(allowedEmailsRaw.get().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (allowedEmails.isEmpty()) {
            return; // allowlist disabled (dev/test)
        }

        if (identity.isAnonymous()) {
            return; // no principal — public route or dev filter not yet run
        }

        // In Quarkus OIDC service mode the principal is an OidcJwtCallerPrincipal
        // which implements JsonWebToken — cast to access claims directly.
        String email = null;
        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            email = jwt.getClaim("email");
        }
        // Fallback: some Quarkus OIDC versions also expose claims via getAttribute
        if (email == null) {
            Object attr = identity.getAttribute("email");
            if (attr != null) email = attr.toString();
        }

        if (email == null || !allowedEmails.contains(email)) {
            ctx.abortWith(
                Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"Access denied\"}")
                    .build()
            );
        }
    }
}
