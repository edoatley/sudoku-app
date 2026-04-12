package com.sudoku.game;

import com.sudoku.dto.GameState;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * API-level tests for {@link GameResource}.
 *
 * <p>Uses {@code @QuarkusTest}, which starts the app in-process via the Quarkus Lambda mock
 * event server. {@code @InjectMock} replaces {@link GameRepository} with a Mockito mock so that
 * {@link DynamoDbGameRepository} is never instantiated — no DynamoDB connection is required.
 *
 * <p>The real DynamoDB integration is exercised separately by the Docker Compose integration tests.
 */
@QuarkusTest
class GameResourceTest {

    @InjectMock
    GameRepository gameRepository;

    private static final String USER_ID = "local-dev-user";

    private static final List<List<Integer>> GRID = List.of(
            List.of(5, 3, 0, 0, 7, 0, 0, 0, 0),
            List.of(6, 0, 0, 1, 9, 5, 0, 0, 0),
            List.of(0, 9, 8, 0, 0, 0, 0, 6, 0),
            List.of(8, 0, 0, 0, 6, 0, 0, 0, 3),
            List.of(4, 0, 0, 8, 0, 3, 0, 0, 1),
            List.of(7, 0, 0, 0, 2, 0, 0, 0, 6),
            List.of(0, 6, 0, 0, 0, 0, 2, 8, 0),
            List.of(0, 0, 0, 4, 1, 9, 0, 0, 5),
            List.of(0, 0, 0, 0, 8, 0, 0, 7, 9)
    );

    private static List<List<List<Integer>>> emptyCandidates() {
        return List.of(
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of()
        );
    }

    @Test
    void postGames_createsGameAndReturns201() {
        // save() is void — default Mockito no-op is correct, no stub needed

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("difficulty", "easy"))
        .when()
                .post("/games")
        .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("userId", equalTo(USER_ID))
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

        GameState saved = new GameState(USER_ID, gameId, "medium", GRID, null, GRID, emptyCandidates(), 0, "IN_PROGRESS", 0);
        when(gameRepository.findById(USER_ID, gameId)).thenReturn(Optional.of(saved));

        given()
        .when()
                .get("/games/{gameId}", gameId)
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("userId", equalTo(USER_ID))
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

        GameState initial = new GameState(USER_ID, gameId, "easy", GRID, null, GRID, emptyCandidates(), 0, "IN_PROGRESS", 0);
        GameState updated = new GameState(USER_ID, gameId, "easy", GRID, null, GRID, emptyCandidates(), 60, "IN_PROGRESS", 0);
        when(gameRepository.findById(USER_ID, gameId))
                .thenReturn(Optional.of(initial))
                .thenReturn(Optional.of(updated));

        given()
        .when()
                .get("/games/{gameId}", gameId)
        .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "currentGrid", GRID,
                        "candidates", Collections.emptyList(),
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

        GameState initial = new GameState(USER_ID, gameId, "easy", GRID, null, GRID, emptyCandidates(), 0, "IN_PROGRESS", 0);
        GameState solved  = new GameState(USER_ID, gameId, "easy", GRID, null, GRID, emptyCandidates(), 300, "SOLVED", 0);
        when(gameRepository.findById(USER_ID, gameId))
                .thenReturn(Optional.of(initial))
                .thenReturn(Optional.of(solved));

        given()
        .when()
                .get("/games/{gameId}", gameId)
        .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "currentGrid", GRID,
                        "candidates", Collections.emptyList(),
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
        when(gameRepository.findById(eq(USER_ID), anyString())).thenReturn(Optional.empty());

        given()
        .when()
                .get("/games/{gameId}", "00000000-0000-0000-0000-000000000000")
        .then()
                .statusCode(404);
    }
    @Test
    void getCurrentGame_whenInProgress_returns200WithGame() {
        String gameId = "in-progress-id";
        GameState inProgress = new GameState(USER_ID, gameId, "medium", GRID, null, GRID, emptyCandidates(), 45, "IN_PROGRESS", 0);
        when(gameRepository.findInProgress(USER_ID)).thenReturn(Optional.of(inProgress));

        given()
        .when()
                .get("/games/current")
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("gameId", equalTo(gameId))
                .body("status", equalTo("IN_PROGRESS"))
                .body("timeSpentSeconds", equalTo(45));
    }

    @Test
    void getCurrentGame_whenNone_returns204() {
        when(gameRepository.findInProgress(USER_ID)).thenReturn(Optional.empty());

        given()
        .when()
                .get("/games/current")
        .then()
                .statusCode(204);
    }

}
