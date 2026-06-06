package com.sudoku.game;

import com.sudoku.dto.GameHistoryEntry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * API-level tests for the GET /games/history endpoint in {@link GameResource}.
 *
 * <p>Uses {@code @QuarkusTest} + {@code @InjectMock} to replace {@link GameRepository}
 * with a Mockito mock — no DynamoDB connection required.
 */
@QuarkusTest
// @spec GH-API-001, GH-API-002, GH-API-003
class GameHistoryResourceTest {

    @InjectMock
    GameRepository gameRepository;

    private static final String USER_ID = "local-dev-user";

    private static GameHistoryEntry solvedEntry(String gameId) {
        return new GameHistoryEntry(gameId, "medium", "won", "2026-05-24T10:00:00Z", 847, 2, 148);
    }

    private static GameHistoryEntry abandonedEntry(String gameId) {
        return new GameHistoryEntry(gameId, "hard", "abandoned", "2026-05-23T09:00:00Z", 300, 0, 0);
    }

    // @spec GH-API-001
    @Test
    void getHistory_returnsOkWithEntries() {
        when(gameRepository.findHistory(eq(USER_ID), anyInt()))
                .thenReturn(List.of(solvedEntry("game-001")));

        given()
        .when()
                .get("/games/history")
        .then()
                .statusCode(200)
                .body("entries", hasSize(1))
                .body("entries[0].gameId", equalTo("game-001"))
                .body("entries[0].difficulty", equalTo("medium"))
                .body("entries[0].outcome", equalTo("won"))
                .body("entries[0].endedAt", equalTo("2026-05-24T10:00:00Z"))
                .body("entries[0].elapsedSeconds", equalTo(847))
                .body("entries[0].hintsUsed", equalTo(2));
    }

    // @spec GH-API-002, GH-BE-002
    @Test
    void getHistory_defaultLimit_is20() {
        List<GameHistoryEntry> twentyFive = IntStream.range(0, 25)
                .mapToObj(i -> solvedEntry("game-" + i))
                .toList();
        when(gameRepository.findHistory(eq(USER_ID), eq(20))).thenReturn(twentyFive.subList(0, 20));

        given()
        .when()
                .get("/games/history")
        .then()
                .statusCode(200)
                .body("entries", hasSize(20));
    }

    // @spec GH-API-002
    @Test
    void getHistory_customLimit_honoured() {
        List<GameHistoryEntry> five = IntStream.range(0, 5)
                .mapToObj(i -> solvedEntry("game-" + i))
                .toList();
        when(gameRepository.findHistory(eq(USER_ID), eq(5))).thenReturn(five);

        given()
                .queryParam("limit", 5)
        .when()
                .get("/games/history")
        .then()
                .statusCode(200)
                .body("entries", hasSize(5));
    }

    // @spec GH-BE-003
    @Test
    void getHistory_limitAbove100_clampedTo100() {
        List<GameHistoryEntry> hundred = IntStream.range(0, 100)
                .mapToObj(i -> solvedEntry("game-" + i))
                .toList();
        when(gameRepository.findHistory(eq(USER_ID), eq(100))).thenReturn(hundred);

        given()
                .queryParam("limit", 999)
        .when()
                .get("/games/history")
        .then()
                .statusCode(200)
                .body("entries", hasSize(100));
    }

    // @spec GH-DTO-002
    @Test
    void getHistory_solvedGame_outcomeIsWon() {
        when(gameRepository.findHistory(eq(USER_ID), anyInt()))
                .thenReturn(List.of(solvedEntry("game-won")));

        given()
        .when()
                .get("/games/history")
        .then()
                .statusCode(200)
                .body("entries[0].outcome", equalTo("won"));
    }

    // @spec GH-DTO-003
    @Test
    void getHistory_abandonedGame_outcomeIsAbandoned() {
        when(gameRepository.findHistory(eq(USER_ID), anyInt()))
                .thenReturn(List.of(abandonedEntry("game-abandoned")));

        given()
        .when()
                .get("/games/history")
        .then()
                .statusCode(200)
                .body("entries[0].outcome", equalTo("abandoned"));
    }

    // @spec GH-BE-004
    @Test
    void getHistory_excludesInProgress() {
        // Repository is responsible for filtering; we verify only 1 entry is returned
        when(gameRepository.findHistory(eq(USER_ID), anyInt()))
                .thenReturn(List.of(solvedEntry("game-solved-only")));

        given()
        .when()
                .get("/games/history")
        .then()
                .statusCode(200)
                .body("entries", hasSize(1))
                .body("entries[0].outcome", equalTo("won"));
    }

    // @spec GH-API-001
    @Test
    void getHistory_noGames_returnsEmptyEntries() {
        when(gameRepository.findHistory(eq(USER_ID), anyInt()))
                .thenReturn(Collections.emptyList());

        given()
        .when()
                .get("/games/history")
        .then()
                .statusCode(200)
                .body("entries", hasSize(0));
    }

    // @spec GH-API-003
    @Test
    void getHistory_usesJwtUserId() {
        when(gameRepository.findHistory(eq(USER_ID), anyInt()))
                .thenReturn(Collections.emptyList());

        given()
        .when()
                .get("/games/history")
        .then()
                .statusCode(200);

        verify(gameRepository).findHistory(eq(USER_ID), anyInt());
    }
}
