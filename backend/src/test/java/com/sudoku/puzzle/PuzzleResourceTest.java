package com.sudoku.puzzle;

import com.sudoku.domain.Grid;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
// @spec PG-API-001, PG-API-002, PG-API-003, PG-API-004, PG-API-010, PG-API-011, PG-API-012
class PuzzleResourceTest {

    // Partial easy board (0 = empty) — same as MockSudokuServiceTest.EASY_GRID
    private static final Grid EASY_GRID = Grid.of(List.of(
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

    // Fully solved board (no empty cells)
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

    // ---- GET /api/v1/puzzles/generate ----

    @Test
    void generate_defaultDifficulty_returns200WithPuzzle() {
        given()
            .when().get("/puzzles/generate")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("originalGrid.rows", hasSize(9))
                .body("originalGrid.rows[0]", hasSize(9))
                .body("difficulty", notNullValue());
    }

    @Test
    void generate_easyDifficulty_returns200() {
        given()
            .queryParam("difficulty", "easy")
            .when().get("/puzzles/generate")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("difficulty", equalTo("easy"))
                .body("originalGrid.rows", hasSize(9));
    }

    @Test
    void generate_hardDifficulty_returns200() {
        given()
            .queryParam("difficulty", "hard")
            .when().get("/puzzles/generate")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("difficulty", equalTo("hard"));
    }

    // ---- POST /api/v1/puzzles/validate ----

    @Test
    void validate_validPartialBoard_returnsIsValidTrue() {
        given()
            .contentType(ContentType.JSON)
            .body(new BoardRequestBody(EASY_GRID))
            .when().post("/puzzles/validate")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("isValid", is(true))
                .body("isSolved", is(false))
                .body("errors", empty());
    }

    @Test
    void validate_solvedBoard_returnsIsSolvedTrue() {
        given()
            .contentType(ContentType.JSON)
            .body(new BoardRequestBody(SOLVED_GRID))
            .when().post("/puzzles/validate")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("isValid", is(true))
                .body("isSolved", is(true))
                .body("errors", empty());
    }

    @Test
    void validate_boardWithRowDuplicate_returnsErrorCoordinates() {
        // Row 0: put 5 at col 2 — duplicates (0,0)=5
        Grid conflicting = withCell(EASY_GRID, 0, 2, 5);

        given()
            .contentType(ContentType.JSON)
            .body(new BoardRequestBody(conflicting))
            .when().post("/puzzles/validate")
            .then()
                .statusCode(200)
                .body("isValid", is(false))
                .body("errors", hasSize(greaterThanOrEqualTo(2)));
    }

    @Test
    void validate_withSolution_wrongButNoDuplicate_returnsInvalid() {
        // Cell (0,2) is empty in EASY_GRID; SOLVED_GRID has 4 there.
        // Fill it with 1 — no duplicate in row/col/block, but wrong answer.
        // Without solutionGrid this would return isValid=true (old bug).
        Grid grid = withCell(EASY_GRID, 0, 2, 1);

        given()
            .contentType(ContentType.JSON)
            .body(new BoardRequestBodyWithSolution(grid, SOLVED_GRID))
            .when().post("/puzzles/validate")
            .then()
                .statusCode(200)
                .body("isValid", is(false))
                .body("errors", hasSize(1))
                .body("errors[0].row", is(0))
                .body("errors[0].col", is(2));
    }

    @Test
    void validate_withSolution_wrongButNoDuplicate_withoutSolution_returnsValid() {
        // Same wrong cell but no solutionGrid supplied — falls back to duplicate detection,
        // which sees no conflict and reports valid. Documents the known limitation.
        Grid grid = withCell(EASY_GRID, 0, 2, 1);

        given()
            .contentType(ContentType.JSON)
            .body(new BoardRequestBody(grid))
            .when().post("/puzzles/validate")
            .then()
                .statusCode(200)
                .body("isValid", is(true));
    }

    // ---- POST /api/v1/puzzles/hint ----

    @Test
    void hint_partialBoard_returns200WithHint() {
        given()
            .contentType(ContentType.JSON)
            .body(new BoardRequestBody(EASY_GRID))
            .when().post("/puzzles/hint")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("techniqueName", notNullValue())
                .body("nudge", notNullValue());
    }

    @Test
    void hint_partialBoard_returnsStrategyRank() {
        given()
            .contentType(ContentType.JSON)
            .body(new BoardRequestBody(EASY_GRID))
            .when().post("/puzzles/hint")
            .then()
                .statusCode(200)
                .body("strategyRank", greaterThan(0));
    }

    @Test
    void hint_withExcludedNakedSingleRank_returnsDifferentTechnique() {
        // First, get the hint without exclusion to confirm it's Naked Single (rank 20)
        String firstTechnique = given()
            .contentType(ContentType.JSON)
            .body(new BoardRequestBody(EASY_GRID))
            .when().post("/puzzles/hint")
            .then()
                .statusCode(200)
                .extract().path("techniqueName");

        // Now exclude rank 20 (Naked Single) and verify we get a different technique
        given()
            .contentType(ContentType.JSON)
            .body(new BoardRequestBodyWithExclusions(EASY_GRID, List.of(20)))
            .when().post("/puzzles/hint")
            .then()
                .statusCode(200)
                .body("techniqueName", not(equalTo(firstTechnique)));
    }

    @Test
    void hint_solvedBoard_returns204() {
        given()
            .contentType(ContentType.JSON)
            .body(new BoardRequestBody(SOLVED_GRID))
            .when().post("/puzzles/hint")
            .then()
                .statusCode(204);
    }

    // ---- POST /api/v1/puzzles/candidates ----

    @Test
    void candidates_partialBoard_returns200WithCandidateGrid() {
        given()
            .contentType(ContentType.JSON)
            .body(new BoardRequestBody(EASY_GRID))
            .when().post("/puzzles/candidates")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("candidatesGrid.rows", hasSize(9))
                .body("candidatesGrid.rows[0]", hasSize(9));
    }

    @Test
    void candidates_filledCell_returnsEmptyCandidateList() {
        given()
            .contentType(ContentType.JSON)
            .body(new BoardRequestBody(EASY_GRID))
            .when().post("/puzzles/candidates")
            .then()
                .statusCode(200)
                // cell (0,0) = 5 (filled) → empty candidate list
                .body("candidatesGrid.rows[0][0]", empty());
    }

    // ---- helpers ----

    private Grid withCell(Grid original, int row, int col, int value) {
        List<List<Integer>> copy = new ArrayList<>();
        for (int r = 0; r < original.rows().size(); r++) {
            List<Integer> origRow = original.rows().get(r);
            if (r == row) {
                List<Integer> newRow = new ArrayList<>(origRow);
                newRow.set(col, value);
                copy.add(newRow);
            } else {
                copy.add(origRow);
            }
        }
        return Grid.of(copy);
    }

    // Simple wrapper so Jackson can serialize the request body with {"rows": [...]} format
    record BoardRequestBody(Grid currentGrid) {}

    record BoardRequestBodyWithSolution(Grid currentGrid, Grid solutionGrid) {}

    record BoardRequestBodyWithExclusions(Grid currentGrid, List<Integer> excludedRanks) {}
}
