package com.sudoku.developer;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Regression test for the PII leak fixed by this change (see
 * docs/planning/old/admin-namespace-security-fix.md and
 * docs/planning/infra-review.md finding H1): an unauthenticated request used
 * to be able to scan and return the entire Games/Players tables via
 * DevDataResource. That class is deleted; these paths must never resolve to
 * anything again.
 */
@QuarkusTest
// @spec UM-BE-064
class DevDataRouteRemovedTest {

    @Test
    void devDataGames_returns404_resourceDeleted() {
        given().when().get("/dev/data/games").then().statusCode(404);
    }

    @Test
    void devDataPlayers_returns404_resourceDeleted() {
        given().when().get("/dev/data/players").then().statusCode(404);
    }
}
