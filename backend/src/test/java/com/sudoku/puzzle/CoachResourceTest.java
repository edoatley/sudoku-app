package com.sudoku.puzzle;

import com.sudoku.domain.Grid;
import com.sudoku.dto.ChatMessage;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

// @spec SC-API-002, SC-API-003, SC-API-004, SC-API-010, SC-API-011, SC-API-012
@QuarkusTest
class CoachResourceTest {

    private static final Grid PARTIAL_GRID = Grid.of(List.of(
            List.of(5, 3, 0, 0, 7, 0, 0, 0, 0),
            List.of(6, 0, 0, 1, 9, 5, 0, 0, 0),
            List.of(0, 9, 8, 0, 0, 0, 0, 6, 0),
            List.of(8, 0, 0, 0, 6, 0, 0, 0, 3),
            List.of(4, 0, 0, 8, 0, 3, 0, 0, 1),
            List.of(7, 0, 0, 0, 2, 0, 0, 0, 6),
            List.of(0, 6, 0, 0, 0, 0, 2, 8, 0),
            List.of(0, 0, 0, 4, 1, 9, 0, 0, 5),
            List.of(0, 0, 0, 0, 8, 0, 0, 7, 9)
    ));

    // ---- POST /api/v1/puzzles/coach ----

    @Test
    void coach_validRequest_returns200WithCoachResponseShape() {
        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, List.of(), "I'm stuck"))
            .when().post("/puzzles/coach")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("aiMessage", notNullValue())
                .body("hint", notNullValue())
                .body("hint.techniqueName", notNullValue())
                .body("hint.nudge", notNullValue())
                .body("hint.highlightCells", notNullValue())
                .body("revealHint", notNullValue());
    }

    @Test
    void coach_emptyHistory_returns200() {
        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, List.of(), "Hello"))
            .when().post("/puzzles/coach")
            .then()
                .statusCode(200)
                .body("aiMessage", not(emptyString()));
    }

    @Test
    void coach_withConversationHistory_returns200() {
        List<ChatMessage> history = List.of(
                new ChatMessage("assistant", "Welcome! Let me take a look."),
                new ChatMessage("user", "I don't know where to start.")
        );

        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, history, "Tell me more"))
            .when().post("/puzzles/coach")
            .then()
                .statusCode(200)
                .body("aiMessage", notNullValue());
    }

    @Test
    void coach_historyExceedingSixMessages_returns200AfterTrimming() {
        // @spec SC-API-004 — backend trims rather than rejects
        List<ChatMessage> longHistory = List.of(
                new ChatMessage("user", "msg1"),
                new ChatMessage("assistant", "msg2"),
                new ChatMessage("user", "msg3"),
                new ChatMessage("assistant", "msg4"),
                new ChatMessage("user", "msg5"),
                new ChatMessage("assistant", "msg6"),
                new ChatMessage("user", "msg7"),
                new ChatMessage("assistant", "msg8")
        );

        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, longHistory, "Help"))
            .when().post("/puzzles/coach")
            .then()
                .statusCode(200);
    }

    @Test
    void coach_nullBoard_returns400() {
        // @spec SC-API-002
        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(null, List.of(), "Help"))
            .when().post("/puzzles/coach")
            .then()
                .statusCode(400);
    }

    @Test
    void coach_blankUserMessage_returns400() {
        // @spec SC-API-003
        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, List.of(), "   "))
            .when().post("/puzzles/coach")
            .then()
                .statusCode(400);
    }

    @Test
    void coach_nullUserMessage_returns400() {
        // @spec SC-API-003
        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, List.of(), null))
            .when().post("/puzzles/coach")
            .then()
                .statusCode(400);
    }

    @Test
    void coach_revealHintIsFalseInStub() {
        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, List.of(), "I'm stuck"))
            .when().post("/puzzles/coach")
            .then()
                .statusCode(200)
                .body("revealHint", is(false));
    }

    // ---- helpers ----

    record CoachRequestBody(Grid board, List<ChatMessage> history, String userMessage) {}
}
