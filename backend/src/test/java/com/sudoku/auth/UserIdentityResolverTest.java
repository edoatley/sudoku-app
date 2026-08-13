package com.sudoku.auth;

import io.quarkus.security.UnauthorizedException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the hardened canonical-identity resolution.
 *
 * @spec UM-GCP-003, UM-GCP-004
 */
class UserIdentityResolverTest {

    // A Principal that also implements JsonWebToken so we can exercise the token path.
    interface JwtPrincipal extends Principal, JsonWebToken {}

    private UserIdentityResolver resolver;
    private SecurityIdentity identity;

    @BeforeEach
    void setUp() throws Exception {
        resolver = new UserIdentityResolver();
        identity = mock(SecurityIdentity.class);
        Field f = UserIdentityResolver.class.getDeclaredField("identity");
        f.setAccessible(true);
        f.set(resolver, identity);
    }

    /** Stub a Firebase-shaped token: firebase.sign_in_provider (+ optional identities) and subject/uid. */
    private void stubFirebaseToken(Map<String, Object> firebaseClaim, String uid) {
        JwtPrincipal jwt = mock(JwtPrincipal.class);
        when(jwt.getClaim("firebase")).thenReturn(firebaseClaim);
        when(jwt.getSubject()).thenReturn(uid);
        when(identity.getPrincipal()).thenReturn(jwt);
    }

    @Test
    void googleProvider_returnsRawGoogleSub() {
        stubFirebaseToken(Map.of(
                "sign_in_provider", "google.com",
                "identities", Map.of("google.com", List.of("112233445566778899000"))),
                "firebase-uid-ignored");

        assertEquals("112233445566778899000", resolver.resolveUserId());
    }

    @Test
    void passwordProvider_returnsNamespacedUid() {
        stubFirebaseToken(Map.of("sign_in_provider", "password"), "abc123UID");

        assertEquals("firebase:abc123UID", resolver.resolveUserId());
    }

    @Test
    void unknownProvider_rejects() {
        stubFirebaseToken(Map.of("sign_in_provider", "anonymous"), "anon-uid");

        assertThrows(UnauthorizedException.class, () -> resolver.resolveUserId());
    }

    @Test
    void googleProvider_missingIdentities_rejects() {
        stubFirebaseToken(Map.of("sign_in_provider", "google.com"), "firebase-uid");

        assertThrows(UnauthorizedException.class, () -> resolver.resolveUserId());
    }

    @Test
    void cognitoToken_noFirebaseClaim_returnsSubject() {
        JwtPrincipal jwt = mock(JwtPrincipal.class);
        when(jwt.getClaim("firebase")).thenReturn(null);
        when(jwt.getSubject()).thenReturn("cognito-sub-uuid");
        when(identity.getPrincipal()).thenReturn(jwt);

        assertEquals("cognito-sub-uuid", resolver.resolveUserId());
    }

    @Test
    void nonJwtPrincipal_rejects() {
        when(identity.getPrincipal()).thenReturn(mock(Principal.class));

        assertThrows(UnauthorizedException.class, () -> resolver.resolveUserId());
    }

    // ── Regression: real OIDC returns the nested `firebase` claim as JSON-P (JsonObject/JsonString),
    //    not a java Map<String,String>. The Map-based tests above never exercised that, so the
    //    String-literal comparisons silently failed for EVERY real token (401). These feed the
    //    production types. @spec UM-GCP-003, UM-GCP-004
    private void stubFirebaseJsonP(JsonObject firebaseClaim, String uid) {
        JwtPrincipal jwt = mock(JwtPrincipal.class);
        when(jwt.getClaim("firebase")).thenReturn(firebaseClaim);
        when(jwt.getSubject()).thenReturn(uid);
        when(identity.getPrincipal()).thenReturn(jwt);
    }

    @Test
    void googleProvider_jsonpClaim_returnsRawGoogleSub() {
        JsonObject firebase = Json.createObjectBuilder()
                .add("sign_in_provider", "google.com")
                .add("identities", Json.createObjectBuilder()
                        .add("google.com", Json.createArrayBuilder().add("112233445566778899000")))
                .build();
        stubFirebaseJsonP(firebase, "firebase-uid-ignored");

        assertEquals("112233445566778899000", resolver.resolveUserId());
    }

    @Test
    void passwordProvider_jsonpClaim_returnsNamespacedUid() {
        JsonObject firebase = Json.createObjectBuilder().add("sign_in_provider", "password").build();
        stubFirebaseJsonP(firebase, "abc123UID");

        assertEquals("firebase:abc123UID", resolver.resolveUserId());
    }
}
