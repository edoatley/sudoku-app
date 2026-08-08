package com.sudoku.web.filter;

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

        // Reject if the email claim is missing or not on the allowlist. The email_verified
        // requirement applies only to Firebase (GCP) tokens: there in-app validation is the sole
        // gate and Firebase reports email_verified truthfully (true for verified Google logins),
        // so it closes the gap where an unverified, attacker-chosen email could match the allowlist.
        // It is NOT enforced for Cognito (AWS) tokens: Cognito sets email_verified=false for every
        // external-provider (Google-federated) user even though the email came from Google, so
        // enforcing it would lock out legitimate users — and on AWS the token is additionally gated
        // by the API Gateway Cognito authorizer and self-signup is disabled, so the email cannot be
        // attacker-chosen.
        // @spec UM-BE-020, UM-GCP-005
        if (email == null || !allowedEmails.contains(email) || (isFirebaseToken() && !isEmailVerified())) {
            ctx.abortWith(
                Response.status(Response.Status.FORBIDDEN)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"Access denied\"}")
                    .build()
            );
        }
    }

    /**
     * True if the caller's token is a Firebase (GCP Identity Platform) token, identified by the
     * presence of a {@code firebase} claim — the same Cognito-vs-Firebase signal
     * {@link com.sudoku.auth.UserIdentityResolver} uses. Cognito (AWS) tokens carry no such claim.
     */
    private boolean isFirebaseToken() {
        return identity.getPrincipal() instanceof JsonWebToken jwt && jwt.getClaim("firebase") != null;
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

    /**
     * Reads the {@code email_verified} claim from the JWT (or identity attributes).
     * Both Cognito ID tokens and Firebase tokens emit it as a boolean; a Google-federated
     * login is always verified, and the provisioned password test user is marked verified.
     *
     * @return true only if the claim is present and truthy
     */
    private boolean isEmailVerified() {
        Object verified = null;

        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            verified = jwt.getClaim("email_verified");
        }
        if (verified == null) {
            verified = identity.getAttribute("email_verified");
        }

        if (verified instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(verified));
    }
}
