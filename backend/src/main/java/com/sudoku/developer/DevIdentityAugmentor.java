package com.sudoku.developer;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Map;

/**
 * Dev/IT/test-only identity provider. OIDC is disabled in these profiles, so every request would
 * otherwise be anonymous — this augmentor replaces the anonymous identity with a <b>Firebase-shaped
 * mock {@link JsonWebToken}</b> (a {@code google.com} identity for {@code local-dev-user}), so
 * {@code UserIdentityResolver} and the request filters run their <b>production logic unchanged</b>
 * with no dev-only carve-out of the provider allow-list. Compiled out of the production artifact by
 * {@code @IfBuildProfile}.
 *
 * <p>The mock carries the {@code administrators} group so the local {@code /admin/*} data browser
 * keeps working without a real token (the deny path is covered by {@code AdminAuthorizationFilterTest}).
 *
 * <p>Set {@code sudoku.dev.mock-identity.enabled=false} to leave requests anonymous — used by the
 * route-coverage test to assert protected routes reject anonymous callers.
 *
 * @spec UM-GCP-009
 */
@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "it", "test"})
public class DevIdentityAugmentor implements SecurityIdentityAugmentor {

    private static final String MOCK_USER_ID = "local-dev-user";

    @ConfigProperty(name = "sudoku.dev.mock-identity.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "app.admin.group", defaultValue = "administrators")
    String adminGroup;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        // A real token is present (or mocking disabled): leave the identity untouched.
        if (!enabled || !identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }

        SecurityIdentity mock = QuarkusSecurityIdentity.builder()
                .setPrincipal(buildMockToken())
                .addRole("user")
                .build();
        return Uni.createFrom().item(mock);
    }

    private JsonWebToken buildMockToken() {
        Map<String, Object> claims = Map.of(
                "sub", MOCK_USER_ID,
                "email", "local-dev@example.com",
                "email_verified", true,
                "name", "Local Dev",
                "firebase", Map.of(
                        "sign_in_provider", "google.com",
                        "identities", Map.of("google.com", List.of(MOCK_USER_ID))),
                "cognito:groups", List.of(adminGroup));
        return new MockJsonWebToken(MOCK_USER_ID, claims);
    }
}
