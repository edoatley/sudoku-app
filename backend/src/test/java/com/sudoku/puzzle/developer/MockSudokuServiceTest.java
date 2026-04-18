package com.sudoku.puzzle.developer;

import com.sudoku.dto.BoardRequest;
import com.sudoku.dto.CandidatesResponse;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.ValidationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// @spec PG-DEV-001, PG-DEV-002, PG-DEV-003, PG-DEV-004
class MockSudokuServiceTest {

    private MockSudokuService service;

    private static final List<List<Integer>> EASY_GRID = List.of(
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

    // Known correct solution for EASY_GRID
    private static final List<List<Integer>> SOLVED_GRID = List.of(
            List.of(5, 3, 4, 6, 7, 8, 9, 1, 2),
            List.of(6, 7, 2, 1, 9, 5, 3, 4, 8),
            List.of(1, 9, 8, 3, 4, 2, 5, 6, 7),
            List.of(8, 5, 9, 7, 6, 1, 4, 2, 3),
            List.of(4, 2, 6, 8, 5, 3, 7, 9, 1),
            List.of(7, 1, 3, 9, 2, 4, 8, 5, 6),
            List.of(9, 6, 1, 5, 3, 7, 2, 8, 4),
            List.of(2, 8, 7, 4, 1, 9, 6, 3, 5),
            List.of(3, 4, 5, 2, 8, 6, 1, 7, 9)
    );

    @BeforeEach
    void setUp() {
        service = new MockSudokuService();
    }

    // ---- getCandidates tests ----

    @Test
    void getCandidates_easyGrid_returnsCorrectCandidates() {
        CandidatesResponse response = service.getCandidates(new BoardRequest(EASY_GRID));
        List<List<List<Integer>>> grid = response.candidatesGrid();

        // Cell (0,2) is empty; row has 5,3,7; col has 8,4,7; block(0) has 5,3,6,9,8 → candidates [1,2,4]
        List<Integer> cell02 = grid.get(0).get(2);
        assertEquals(List.of(1, 2, 4), cell02);
    }

    @Test
    void getCandidates_filledCells_returnEmptyList() {
        CandidatesResponse response = service.getCandidates(new BoardRequest(EASY_GRID));
        List<List<List<Integer>>> grid = response.candidatesGrid();

        // Cell (0,0) = 5 (filled) → empty candidates
        assertEquals(List.of(), grid.get(0).get(0));
        // Cell (1,3) = 1 (filled) → empty candidates
        assertEquals(List.of(), grid.get(1).get(3));
    }

    @Test
    void getCandidates_emptyBoard_allNineCandidates() {
        List<List<Integer>> emptyGrid = List.of(
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
                List.of(0, 0, 0, 0, 0, 0, 0, 0, 0)
        );
        CandidatesResponse response = service.getCandidates(new BoardRequest(emptyGrid));
        List<List<List<Integer>>> grid = response.candidatesGrid();

        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), grid.get(r).get(c),
                        "Cell (" + r + "," + c + ") should have all 9 candidates");
            }
        }
    }

    // ---- validatePuzzle tests ----

    @Test
    void validatePuzzle_validBoard_noErrors() {
        ValidationResponse response = service.validatePuzzle(new BoardRequest(EASY_GRID));

        assertTrue(response.isValid());
        assertFalse(response.isSolved());
        assertTrue(response.errors().isEmpty());
    }

    @Test
    void validatePuzzle_solvedBoard_isSolved() {
        ValidationResponse response = service.validatePuzzle(new BoardRequest(SOLVED_GRID));

        assertTrue(response.isValid());
        assertTrue(response.isSolved());
        assertTrue(response.errors().isEmpty());
    }

    @Test
    void validatePuzzle_rowDuplicate_reportsErrors() {
        // Modify row 0: put 5 in position (0,4) to duplicate the 5 at (0,0)
        List<List<Integer>> grid = mutableCopy(EASY_GRID);
        grid.get(0).set(4, 5); // was 7, now 5 — duplicates (0,0)=5

        ValidationResponse response = service.validatePuzzle(new BoardRequest(grid));

        assertFalse(response.isValid());
        assertFalse(response.isSolved());
        List<Coordinate> errors = response.errors();
        assertTrue(errors.contains(new Coordinate(0, 0)), "Cell (0,0) should be in errors");
        assertTrue(errors.contains(new Coordinate(0, 4)), "Cell (0,4) should be in errors");
    }

    @Test
    void validatePuzzle_columnDuplicate_reportsErrors() {
        // Column 0 has 5,6,_,8,4,7,_,_,_ — put 5 at (3,0) to duplicate (0,0)=5
        List<List<Integer>> grid = mutableCopy(EASY_GRID);
        grid.get(3).set(0, 5); // was 8, now 5 — duplicates (0,0)=5 in column 0

        ValidationResponse response = service.validatePuzzle(new BoardRequest(grid));

        assertFalse(response.isValid());
        List<Coordinate> errors = response.errors();
        assertTrue(errors.contains(new Coordinate(0, 0)), "Cell (0,0) should be in errors");
        assertTrue(errors.contains(new Coordinate(3, 0)), "Cell (3,0) should be in errors");
    }

    @Test
    void validatePuzzle_blockDuplicate_reportsErrors() {
        // Block 0 (rows 0-2, cols 0-2) has 5,3,6,9,8 — the 9 is at (2,1)
        // add a 9 at (0,2) to duplicate (2,1)=9 within block 0
        List<List<Integer>> grid = mutableCopy(EASY_GRID);
        grid.get(0).set(2, 9); // was 0, now 9 — duplicates (2,1)=9 in block 0

        ValidationResponse response = service.validatePuzzle(new BoardRequest(grid));

        assertFalse(response.isValid());
        List<Coordinate> errors = response.errors();
        assertTrue(errors.contains(new Coordinate(0, 2)), "Cell (0,2) should be in errors");
        assertTrue(errors.contains(new Coordinate(2, 1)), "Cell (2,1) should be in errors");
    }

    @Test
    void validatePuzzle_multipleConflicts_cellAppearsOnce() {
        List<List<Integer>> grid = mutableCopy(EASY_GRID);
        grid.get(0).set(2, 3); // was 0, now 3 — duplicates (0,1)=3 in row 0 and block 0

        ValidationResponse response = service.validatePuzzle(new BoardRequest(grid));

        assertFalse(response.isValid());
        List<Coordinate> errors = response.errors();
        long count01 = errors.stream().filter(c -> c.row() == 0 && c.col() == 1).count();
        long count02 = errors.stream().filter(c -> c.row() == 0 && c.col() == 2).count();
        assertEquals(1, count01, "Cell (0,1) should appear exactly once in errors");
        assertEquals(1, count02, "Cell (0,2) should appear exactly once in errors");
    }

    // ---- helpers ----

    private List<List<Integer>> mutableCopy(List<List<Integer>> original) {
        List<List<Integer>> copy = new java.util.ArrayList<>();
        for (List<Integer> row : original) {
            copy.add(new java.util.ArrayList<>(row));
        }
        return copy;
    }
}
