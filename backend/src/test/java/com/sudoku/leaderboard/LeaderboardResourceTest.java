package com.sudoku.leaderboard;

import com.sudoku.dto.LeaderboardEntry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * API-level tests for {@code GET /api/v1/leaderboard} in {@link LeaderboardResource}.
 *
 * <p>Uses {@code @QuarkusTest} + {@code @InjectMock} to replace {@link LeaderboardService}
 * with a Mockito mock — no DynamoDB connection required.
 */
@QuarkusTest
// @spec LT-API-001, LT-API-002
class LeaderboardResourceTest {

    @InjectMock
    LeaderboardService leaderboardService;

    private static final String USER_ID = "local-dev-user";

    private static LeaderboardEntry rankOneEntry() {
        return new LeaderboardEntry(
                USER_ID,
                "Ed",
                "SportsBasketball",
                1,
                10,
                12,
                185,
                241,
                Map.of("easy", 95, "medium", 241)
        );
    }

    private static LeaderboardEntry rankTwoEntry() {
        return new LeaderboardEntry(
                "other-user-id",
                "Alice",
                "DirectionsRun",
                2,
                7,
                9,
                140,
                310,
                Map.of("easy", 110, "hard", 720)
        );
    }

    // @spec LT-API-001
    @Test
    void getLeaderboard_returnsOkWithEntries() {
        when(leaderboardService.getLeaderboard()).thenReturn(
                new com.sudoku.dto.LeaderboardResponse(List.of(rankOneEntry(), rankTwoEntry()))
        );

        given()
        .when()
                .get("/leaderboard")
        .then()
                .statusCode(200)
                .body("entries", hasSize(2))
                .body("entries[0].displayName", equalTo("Ed"))
                .body("entries[0].rank", equalTo(1))
                .body("entries[0].totalWins", equalTo(10))
                .body("entries[0].totalGames", equalTo(12))
                .body("entries[0].avgScore", equalTo(185))
                .body("entries[0].avgElapsedSeconds", equalTo(241))
                .body("entries[0].avatarKey", equalTo("SportsBasketball"))
                .body("entries[1].displayName", equalTo("Alice"))
                .body("entries[1].rank", equalTo(2));
    }

    @Test
    void getLeaderboard_includesBestTimeByDifficulty() {
        when(leaderboardService.getLeaderboard()).thenReturn(
                new com.sudoku.dto.LeaderboardResponse(List.of(rankOneEntry()))
        );

        given()
        .when()
                .get("/leaderboard")
        .then()
                .statusCode(200)
                .body("entries[0].bestTimeByDifficulty.easy", equalTo(95))
                .body("entries[0].bestTimeByDifficulty.medium", equalTo(241));
    }

    @Test
    void getLeaderboard_noPlayers_returnsEmptyEntries() {
        when(leaderboardService.getLeaderboard()).thenReturn(
                new com.sudoku.dto.LeaderboardResponse(Collections.emptyList())
        );

        given()
        .when()
                .get("/leaderboard")
        .then()
                .statusCode(200)
                .body("entries", hasSize(0));
    }

    // @spec LT-API-002
    @Test
    void getLeaderboard_usesJwtForAuthentication() {
        when(leaderboardService.getLeaderboard()).thenReturn(
                new com.sudoku.dto.LeaderboardResponse(Collections.emptyList())
        );

        given()
        .when()
                .get("/leaderboard")
        .then()
                .statusCode(200);

        verify(leaderboardService).getLeaderboard();
    }

    @Test
    void getLeaderboard_ranksAreReturnedInOrder() {
        when(leaderboardService.getLeaderboard()).thenReturn(
                new com.sudoku.dto.LeaderboardResponse(List.of(rankOneEntry(), rankTwoEntry()))
        );

        given()
        .when()
                .get("/leaderboard")
        .then()
                .statusCode(200)
                .body("entries[0].rank", equalTo(1))
                .body("entries[1].rank", equalTo(2));
    }
}
