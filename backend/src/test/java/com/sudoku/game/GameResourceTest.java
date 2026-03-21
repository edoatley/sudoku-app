package com.sudoku.game;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * API-level tests for {@link GameResource}.
 *
 * <p>Uses {@code @QuarkusTest}, which starts the app in-process via the Quarkus Lambda mock
 * event server (required because the project uses {@code quarkus-amazon-lambda-rest} — there
 * is no real HTTP server process). Quarkus automatically wires RestAssured to the mock server,
 * so no manual {@code baseURI} setup is needed here.
 *
 * <p>DynamoDB is pointed at LocalStack via {@code src/test/resources/application.properties}.
 * Ensure LocalStack is running with the {@code SudokuGames} table created before executing
 * these tests (the CI workflow handles this automatically).
 */
@QuarkusTest
class GameResourceTest {

    @Test
    void postGames_createsGameAndReturns201() {
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("difficulty", "easy"))
        .when()
            .post("/games")
        .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .body("userId", equalTo("local-dev-user"))
            .body("gameId", notNullValue())
            .body("difficulty", equalTo("easy"))
            .body("originalGrid", hasSize(9))
            .body("currentGrid", hasSize(9))
            .body("candidates", hasSize(9))
            .body("timeSpentSeconds", equalTo(0))
            .body("status", equalTo("IN_PROGRESS"));
    }

    @Test
    void getGame_afterCreate_returnsMatchingState() {
        String gameId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("difficulty", "medium"))
        .when()
            .post("/games")
        .then()
            .statusCode(201)
            .extract().path("gameId");

        given()
        .when()
            .get("/games/{gameId}", gameId)
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("userId", equalTo("local-dev-user"))
            .body("gameId", equalTo(gameId))
            .body("difficulty", equalTo("medium"))
            .body("status", equalTo("IN_PROGRESS"));
    }

    @Test
    void patchGame_updatesTimeAndReturns200() {
        String gameId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("difficulty", "easy"))
        .when()
            .post("/games")
        .then()
            .statusCode(201)
            .extract().path("gameId");

        // Retrieve the original grid to use in the update
        var originalGrid = given()
            .when().get("/games/{gameId}", gameId)
            .then().statusCode(200)
            .extract().path("currentGrid");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "currentGrid", originalGrid,
                "candidates", java.util.Collections.emptyList(),
                "timeSpentSeconds", 60,
                "isComplete", false
            ))
        .when()
            .patch("/games/{gameId}", gameId)
        .then()
            .statusCode(200);

        given()
        .when()
            .get("/games/{gameId}", gameId)
        .then()
            .statusCode(200)
            .body("timeSpentSeconds", equalTo(60));
    }

    @Test
    void patchGame_withIsComplete_setsStatusToSolved() {
        String gameId = given()
            .contentType(ContentType.JSON)
            .body(Map.of("difficulty", "easy"))
        .when()
            .post("/games")
        .then()
            .statusCode(201)
            .extract().path("gameId");

        var originalGrid = given()
            .when().get("/games/{gameId}", gameId)
            .then().statusCode(200)
            .extract().path("currentGrid");

        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "currentGrid", originalGrid,
                "candidates", java.util.Collections.emptyList(),
                "timeSpentSeconds", 300,
                "isComplete", true
            ))
        .when()
            .patch("/games/{gameId}", gameId)
        .then()
            .statusCode(200);

        given()
        .when()
            .get("/games/{gameId}", gameId)
        .then()
            .statusCode(200)
            .body("status", equalTo("SOLVED"));
    }

    @Test
    void getGame_unknownId_returns404() {
        given()
        .when()
            .get("/games/{gameId}", "00000000-0000-0000-0000-000000000000")
        .then()
            .statusCode(404);
    }
}
