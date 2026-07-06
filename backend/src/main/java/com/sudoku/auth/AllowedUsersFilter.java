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

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        this.allowedEmails = Stream.of(allowedEmailsRaw.orElse("").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Filters the incoming request based on the allowed email list.
     *
     * @param ctx the container request context
     */
    @Override
    public void filter(ContainerRequestContext ctx) {

        // If the allowlist is empty or the user is anonymous, skip the check.
        if (allowedEmails.isEmpty() || identity.isAnonymous()) {
            return; 
        }

        String email = getEmail();

        // If the email claim is missing or not in the allowlist, reject the request.
        if (email == null || !allowedEmails.contains(email)) {
            ctx.abortWith(
                Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"Access denied\"}")
                    .build()
            );
        }
    }

    /**
     * Extracts the email claim from the JWT or identity attributes.
     *
     * @return the email address, or null if not present
     */
    private String getEmail() {
        String email = null;

        // Quarkus OIDC exposes the JWT via getPrincipal, so we can extract the email claim from it.
        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            email = jwt.getClaim("email");
        }

        // Fallback: some Quarkus OIDC versions also expose claims via getAttribute
        if (email == null) {
            Object attr = identity.getAttribute("email");
            if (attr != null) email = attr.toString();
        }

        return email;
    }
}
