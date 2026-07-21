package com.sudoku.web;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.Method;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Route-coverage guard for the in-app auth gate. On GCP there is no edge authorizer, so a protected
 * resource missing its {@code @Authenticated} annotation would be a silently open endpoint. This
 * test disables the dev mock identity ({@code DevIdentityAugmentor}) so requests are genuinely
 * anonymous, then asserts every protected route rejects the anonymous caller with 401 while the
 * public routes remain reachable.
 *
 * @spec UM-GCP-001
 */
@QuarkusTest
@TestProfile(RouteAuthorizationTest.AnonymousProfile.class)
class RouteAuthorizationTest {

    /** Leaves requests anonymous by switching off the dev mock identity. */
    public static class AnonymousProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("sudoku.dev.mock-identity.enabled", "false");
        }
    }

    // OIDC is disabled in the test profile, so an anonymous request to an @Authenticated route is
    // rejected with 403 (Quarkus has no auth mechanism to issue a 401 challenge). In production with
    // OIDC enabled the same request is rejected with 401. Either way the route is not open.
    private void assertRejectsAnonymous(Method method, String path) {
        given()
                .contentType("application/json")
                .body("{}")
                .request(method, path)
                .then()
                .statusCode(anyOf(is(401), is(403)));
    }

    private void assertNotAuthGated(Method method, String path) {
        given()
                .request(method, path)
                .then()
                .statusCode(not(anyOf(is(401), is(403))));
    }

    @Test
    void protectedGameRoutes_rejectAnonymous() {
        assertRejectsAnonymous(Method.POST, "/games");
        assertRejectsAnonymous(Method.POST, "/games/from-image");
        assertRejectsAnonymous(Method.GET, "/games/current");
        assertRejectsAnonymous(Method.GET, "/games/any-id");
        assertRejectsAnonymous(Method.PATCH, "/games/any-id");
        assertRejectsAnonymous(Method.GET, "/games/history");
    }

    @Test
    void protectedPlayerRoutes_rejectAnonymous() {
        assertRejectsAnonymous(Method.GET, "/players/me");
        assertRejectsAnonymous(Method.PATCH, "/players/me");
    }

    @Test
    void protectedCoachAndLeaderboardRoutes_rejectAnonymous() {
        assertRejectsAnonymous(Method.POST, "/ai/coach");
        assertRejectsAnonymous(Method.GET, "/leaderboard");
    }

    @Test
    void publicRoutes_allowAnonymous() {
        // Public routes must NOT be auth-gated (their own status may vary, but never 401/403).
        assertNotAuthGated(Method.GET, "/health");
        assertNotAuthGated(Method.GET, "/puzzles/generate");
    }
}
