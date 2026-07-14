package com.sudoku.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudoku.domain.Grid;
import com.sudoku.game.web.PuzzleEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// @spec GL-BE-040, GL-BE-041, GL-BE-042, GL-BE-043, GL-BE-044, GL-BE-046, GL-BE-047
class PuzzleEventLoggerTest {

    private static final String PID = "game-123";
    private static final String USER = "user-abc";

    // A concrete, valid solution so NUMBER correctness is deterministic. cell(0,0)=5, cell(0,2)=4.
    private static final Grid SOLUTION = Grid.of(List.of(
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

    private ObjectMapper objectMapper;
    private PuzzleEventLogger logger;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        logger = new PuzzleEventLogger(objectMapper);
    }

    private static PuzzleEvent number(int r, int c, int v) {
        return new PuzzleEvent("NUMBER", r, c, v, null, 1000L, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static PuzzleEvent undo(int r, int c, int v, int prevV) {
        return new PuzzleEvent("UNDO", r, c, v, null, 1000L, null, null, null, null, null, null, prevV, "NUMBER", null, null, null);
    }

    private JsonNode parse(String line) throws Exception {
        return objectMapper.readTree(line);
    }

    @Test
    void everyLineCarriesTypePidUserAndTs() throws Exception {
        // @spec GL-BE-040
        List<String> lines = logger.buildLines(PID, USER, SOLUTION, List.of(
                new PuzzleEvent("NUMBER_CLEAR", 2, 3, null, null, 1000L, null, null, null, null, null, null, null, null, null, null, null)));

        assertEquals(1, lines.size());
        JsonNode node = parse(lines.get(0));
        assertEquals("NUMBER_CLEAR", node.path("type").asText());
        assertEquals(PID, node.path("pid").asText());
        assertEquals(USER, node.path("userId").asText());
        assertTrue(node.has("ts"));
        assertEquals(2, node.path("r").asInt());
        assertEquals(3, node.path("c").asInt());
    }

    @Test
    void numberEmitsActionThenServerVerdict_correct() throws Exception {
        // @spec GL-BE-041 — NUMBER followed by NUMBER_RESULT with correct=true when it matches the solution
        List<String> lines = logger.buildLines(PID, USER, SOLUTION, List.of(number(0, 0, 5)));

        assertEquals(2, lines.size());
        JsonNode action = parse(lines.get(0));
        JsonNode result = parse(lines.get(1));
        assertEquals("NUMBER", action.path("type").asText());
        assertEquals(5, action.path("v").asInt());
        assertEquals("NUMBER_RESULT", result.path("type").asText());
        assertTrue(result.path("correct").asBoolean());
        assertEquals(0, result.path("r").asInt());
        assertEquals(0, result.path("c").asInt());
    }

    @Test
    void numberResultIsFalseWhenDigitDoesNotMatchSolution() throws Exception {
        // @spec GL-BE-041
        List<String> lines = logger.buildLines(PID, USER, SOLUTION, List.of(number(0, 0, 9)));

        assertEquals(2, lines.size());
        assertFalse(parse(lines.get(1)).path("correct").asBoolean());
    }

    @Test
    void clearAndHintEventsPassThroughWithTheirFields() throws Exception {
        // @spec GL-BE-042
        PuzzleEvent hintReq = new PuzzleEvent("HINT_REQUEST", null, null, null, "cid-9", 1000L,
                null, null, null, null, 3, List.of(1, 2), null, null, null, null, null);
        PuzzleEvent hintResp = new PuzzleEvent("HINT_RESPONSE", null, null, null, "cid-9", 1001L,
                "Naked Single", 4, "easy", true, null, null, null, null,
                "A cell has been reduced to exactly one possible candidate.",
                "Row 1, Column 3 has had every other digit eliminated.",
                "Row 1, Column 3 must be 4.");

        List<String> lines = logger.buildLines(PID, USER, SOLUTION, List.of(hintReq, hintResp));

        assertEquals(2, lines.size());
        JsonNode req = parse(lines.get(0));
        JsonNode resp = parse(lines.get(1));
        assertEquals("HINT_REQUEST", req.path("type").asText());
        assertEquals("cid-9", req.path("cid").asText());
        assertEquals("HINT_RESPONSE", resp.path("type").asText());
        assertEquals("cid-9", resp.path("cid").asText());
        assertEquals("Naked Single", resp.path("techniqueName").asText());
        assertEquals(4, resp.path("strategyRank").asInt());
        assertTrue(resp.path("found").asBoolean());
        // @spec FE-BE-021
        assertEquals("A cell has been reduced to exactly one possible candidate.", resp.path("nudge").asText());
        assertEquals("Row 1, Column 3 has had every other digit eliminated.", resp.path("focus").asText());
        assertEquals("Row 1, Column 3 must be 4.", resp.path("reveal").asText());
    }

    @Test
    void undoEmitsRemovedAndRestoredValuesWithUndoneType() throws Exception {
        // @spec GL-BE-047
        List<String> lines = logger.buildLines(PID, USER, SOLUTION, List.of(undo(0, 0, 5, 0)));

        assertEquals(1, lines.size());
        JsonNode node = parse(lines.get(0));
        assertEquals("UNDO", node.path("type").asText());
        assertEquals(0, node.path("r").asInt());
        assertEquals(0, node.path("c").asInt());
        assertEquals(5, node.path("v").asInt());
        assertEquals(0, node.path("prevV").asInt());
        assertEquals("NUMBER", node.path("undoneType").asText());
    }

    @Test
    void undoOutOfRangeCoordinatesOrDigitAreSkippedNotThrown() {
        // @spec GL-BE-047, GL-BE-043 — same untrusted-input guard as NUMBER
        List<String> lines = assertDoesNotThrow(() -> logger.buildLines(PID, USER, SOLUTION, List.of(
                undo(9, 0, 5, 0),   // row out of range
                undo(0, 0, 0, 0)))); // digit removed out of range

        assertTrue(lines.isEmpty());
    }

    @Test
    void unknownTypeIsSkippedWithoutError() {
        // @spec GL-BE-043
        List<String> lines = logger.buildLines(PID, USER, SOLUTION, List.of(
                new PuzzleEvent("BOGUS", 0, 0, 1, null, 1000L, null, null, null, null, null, null, null, null, null, null, null)));

        assertTrue(lines.isEmpty());
    }

    @Test
    void outOfRangeCoordinatesOrDigitAreSkippedNotThrown() {
        // @spec GL-BE-043 — client-supplied r/c/v are untrusted; a bad value must not throw
        List<String> lines = assertDoesNotThrow(() -> logger.buildLines(PID, USER, SOLUTION, List.of(
                number(9, 0, 5),      // row out of range
                number(0, -1, 5),     // col out of range
                number(0, 0, 0),      // digit out of range (0)
                number(0, 0, 10))));  // digit out of range (10)

        assertTrue(lines.isEmpty());
    }

    @Test
    void processesAtMostFiveHundredEvents() {
        // @spec GL-BE-044
        PuzzleEvent clear = new PuzzleEvent("NUMBER_CLEAR", 0, 0, null, null, 1000L, null, null, null, null, null, null, null, null, null, null, null);
        List<PuzzleEvent> many = java.util.Collections.nCopies(600, clear);

        List<String> lines = logger.buildLines(PID, USER, SOLUTION, many);

        assertEquals(500, lines.size());
    }

    @Test
    void truncationMarkerIsLoggedAsItsOwnLine() throws Exception {
        // @spec GL-BE-044
        List<String> lines = logger.buildLines(PID, USER, SOLUTION, List.of(
                new PuzzleEvent("EVENTS_TRUNCATED", null, null, null, null, 1000L, null, null, null, null, null, null, null, null, null, null, null)));

        assertEquals(1, lines.size());
        assertEquals("EVENTS_TRUNCATED", parse(lines.get(0)).path("type").asText());
    }

    @Test
    void nullEventsProduceNoLines() {
        // @spec GL-BE-045
        assertTrue(logger.buildLines(PID, USER, SOLUTION, null).isEmpty());
    }

    @Test
    void techniqueWithSpecialCharsStillProducesParseableJson() throws Exception {
        // @spec GL-BE-046 — JSON serialized via a library, not string templating
        PuzzleEvent hintResp = new PuzzleEvent("HINT_RESPONSE", null, null, null, "cid-1", 1000L,
                "X-Wing \"tricky\"\nline", 7, "hard", false, null, null, null, null, null, null, null);

        List<String> lines = logger.buildLines(PID, USER, SOLUTION, List.of(hintResp));

        assertEquals("X-Wing \"tricky\"\nline", parse(lines.get(0)).path("techniqueName").asText());
    }
}
