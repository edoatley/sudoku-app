package com.sudoku.puzzle;

import com.sudoku.domain.Grid;
import com.sudoku.dto.ChatMessage;
import com.sudoku.player.PlayerProfile;
import com.sudoku.player.PlayerRepository;
import com.sudoku.player.PlayerService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

// @spec SC-API-002, SC-API-003, SC-API-004, SC-API-010, SC-API-011, SC-API-012
// @spec SC-BE-001, SC-BE-002, SC-BE-003, SC-BE-009
// @spec SC-RL-001, SC-RL-002, SC-RL-003
@QuarkusTest
class CoachResourceTest {

    @InjectMock
    BedrockCoachClient bedrockCoachClient;

    @InjectMock
    PlayerService playerService;

    @InjectMock
    PlayerRepository playerRepository;

    @InjectMock
    CoachRateLimiter rateLimiter;

    private static final PlayerProfile ENABLED_PLAYER = new PlayerProfile(
            "local-dev-user", "dev@example.com", "Dev User", null,
            "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z",
            Boolean.TRUE, 0L, null
    );

    @BeforeEach
    void stubDependencies() {
        when(bedrockCoachClient.call(anyString(), any(), anyList(), any()))
                .thenReturn(new BedrockCoachClient.CallResult(
                        new BedrockCoachClient.AiReply("Let's look at the board together.", false), 1500L));
        when(playerService.getOrCreateProfile(anyString(), any(), any())).thenReturn(ENABLED_PLAYER);
        when(rateLimiter.tryConsume(anyString())).thenReturn(true);
        doNothing().when(playerRepository).incrementCoachTokens(anyString(), anyLong(), anyString());
    }

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

    private static final Grid SOLVED_GRID = Grid.of(List.of(
            List.of(5, 3, 4, 6, 7, 8, 9, 1, 2),
            List.of(6, 7, 2, 1, 9, 5, 3, 4, 8),
            List.of(1, 9, 8, 3, 4, 2, 5, 6, 7),
            List.of(8, 5, 9, 7, 6, 1, 4, 2, 3),
            List.of(4, 2, 6, 8, 5, 3, 7, 9, 1),
            List.of(7, 1, 3, 9, 2, 4, 8, 5, 6),
            List.of(9, 6, 1, 5, 3, 7, 2, 8, 4),
            List.of(2, 8, 7, 4, 1, 9, 6, 3, 5),
            List.of(3, 4, 5, 2, 8, 6, 1, 7, 9)
    ));

    // ---- POST /api/v1/ai/coach ----

    @Test
    void coach_validRequest_returns200WithFullCoachResponseShape() {
        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, List.of(), "I'm stuck"))
            .when().post("/ai/coach")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("aiMessage", not(emptyString()))
                .body("hint", notNullValue())
                .body("hint.techniqueName", notNullValue())
                .body("hint.nudge", notNullValue())
                .body("hint.strategyRank", greaterThan(0))
                .body("hint.highlightCells", notNullValue())
                .body("revealHint", is(false));
    }

    @Test
    void coach_aiMessageIsNonBlank() {
        // @spec SC-BE-003 — aiMessage contains coaching prose (from Bedrock or fallback)
        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, List.of(), "Help me"))
            .when().post("/ai/coach")
            .then()
                .statusCode(200)
                .body("aiMessage", not(emptyString()));
    }

    @Test
    void coach_solvedBoard_returns204() {
        // @spec SC-BE-002
        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(SOLVED_GRID, List.of(), "Am I done?"))
            .when().post("/ai/coach")
            .then()
                .statusCode(204);
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
            .when().post("/ai/coach")
            .then()
                .statusCode(200)
                .body("aiMessage", not(emptyString()));
    }

    @Test
    void coach_historyExceedingSixMessages_returns200AfterTrimming() {
        // @spec SC-API-004 — backend trims rather than rejects
        List<ChatMessage> longHistory = List.of(
                new ChatMessage("user", "msg1"), new ChatMessage("assistant", "msg2"),
                new ChatMessage("user", "msg3"), new ChatMessage("assistant", "msg4"),
                new ChatMessage("user", "msg5"), new ChatMessage("assistant", "msg6"),
                new ChatMessage("user", "msg7"), new ChatMessage("assistant", "msg8")
        );

        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, longHistory, "Help"))
            .when().post("/ai/coach")
            .then()
                .statusCode(200);
    }

    @Test
    void coach_blankUserMessage_returns400() {
        // @spec SC-API-003
        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, List.of(), "   "))
            .when().post("/ai/coach")
            .then()
                .statusCode(400);
    }

    @Test
    void coach_nullUserMessage_returns400() {
        // @spec SC-API-003
        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, List.of(), null))
            .when().post("/ai/coach")
            .then()
                .statusCode(400);
    }

    @Test
    void coach_boardWithWrongRowCount_returns400() {
        // @spec SC-API-002 — Board.fromGrid() throws InvalidGridException → 400
        Grid shortGrid = Grid.of(List.of(
                List.of(5, 3, 0, 0, 7, 0, 0, 0, 0)
        ));

        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(shortGrid, List.of(), "Help"))
            .when().post("/ai/coach")
            .then()
                .statusCode(400);
    }

    @Test
    void coach_hintResponseIsFullyPopulated() {
        // @spec SC-API-011 — hint always fully populated regardless of revealHint
        given()
            .contentType(ContentType.JSON)
            .body(new CoachRequestBody(PARTIAL_GRID, List.of(), "I'm stuck"))
            .when().post("/ai/coach")
            .then()
                .statusCode(200)
                .body("hint.nudge", notNullValue())
                .body("hint.focus", notNullValue())
                .body("hint.reveal", notNullValue())
                .body("hint.markdownSlug", notNullValue())
                .body("hint.difficulty", notNullValue());
    }

    // ---- helpers ----

    record CoachRequestBody(Grid board, List<ChatMessage> history, String userMessage) {}
}
