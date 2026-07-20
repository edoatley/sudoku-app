package com.sudoku.auth;

import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Resolves the canonical application {@code userId} from the authenticated principal.
 *
 * <p>The canonical identity is the <b>Google {@code sub}</b> — the federated-identity subject that
 * is stable across identity providers — so a user's data is keyed identically regardless of which
 * cloud (Cognito on AWS, Identity Platform/Firebase on GCP) brokered the login.
 *
 * <p>Resolution is by token shape:
 * <ul>
 *   <li><b>Firebase token</b> (carries a {@code firebase} claim) — a strict provider allow-list:
 *       {@code google.com} → the Google {@code sub} from {@code firebase.identities["google.com"]};
 *       {@code password} → {@code firebase:<uid>} (namespaced; the CI smoke-test user only);
 *       any other {@code sign_in_provider} → reject (401). Account-linking is not supported.</li>
 *   <li><b>Cognito token</b> (no {@code firebase} claim) — currently {@code jwt.getSubject()}
 *       (the Cognito subject); the deferred AWS re-key switches this to the Google {@code sub} in
 *       the Cognito {@code identities} claim.</li>
 * </ul>
 *
 * <p>In dev/it/test, {@code DevUserFilter} injects a Firebase-shaped mock JWT, so this resolver runs
 * its production logic unchanged — there is no dev-only bypass of the allow-list.
 *
 * @spec UM-GCP-003, UM-GCP-004
 */
@ApplicationScoped
public class UserIdentityResolver {

    static final String FIREBASE_PREFIX = "firebase:";

    @Inject
    SecurityIdentity identity;

    /**
     * @return the canonical userId for the authenticated caller
     * @throws io.quarkus.security.UnauthorizedException if the principal has no recognised identity
     *                                                   provider
     */
    public String resolveUserId() {
        Principal principal = identity.getPrincipal();
        if (!(principal instanceof JsonWebToken jwt)) {
            throw new UnauthorizedException();
        }

        Object firebaseClaim = jwt.getClaim("firebase");
        if (firebaseClaim instanceof Map<?, ?> firebase) {
            return resolveFirebase(firebase, jwt.getSubject());
        }

        // Cognito token (no `firebase` claim). Current AWS behaviour: the Cognito subject.
        // The deferred AWS re-key switches this to the Google sub in the `identities` claim.
        return jwt.getSubject();
    }

    private String resolveFirebase(Map<?, ?> firebase, String uid) {
        Object provider = firebase.get("sign_in_provider");

        if ("google.com".equals(provider)) {
            if (firebase.get("identities") instanceof Map<?, ?> identities
                    && identities.get("google.com") instanceof List<?> googleIds
                    && !googleIds.isEmpty()
                    && googleIds.get(0) != null) {
                return String.valueOf(googleIds.get(0));
            }
            throw new UnauthorizedException();
        }

        if ("password".equals(provider)) {
            return FIREBASE_PREFIX + uid;
        }

        // Any other sign-in provider (anonymous, phone, unknown) is not accepted.
        throw new UnauthorizedException();
    }
}
