package com.sudoku.puzzle;

import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.BoardRequest;
import com.sudoku.dto.CandidatesResponse;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import com.sudoku.dto.ValidationResponse;
import com.sudoku.puzzle.hint.FullHouseStrategy;
import com.sudoku.puzzle.hint.NakedPairStrategy;
import com.sudoku.puzzle.hint.NakedSingleStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SudokuServiceImplTest {

    private SudokuServiceImpl service;

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
        service = new SudokuServiceImpl(List.of());
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

    @Test
    void validatePuzzle_withSolution_correctCell_noErrors() {
        List<List<Integer>> grid = mutableCopy(EASY_GRID);
        grid.get(0).set(2, 4); // correct value per SOLVED_GRID

        ValidationResponse response = service.validatePuzzle(
                new BoardRequest(grid, SOLVED_GRID, null, null));

        assertTrue(response.isValid());
        assertTrue(response.errors().isEmpty());
    }

    @Test
    void validatePuzzle_withSolution_wrongCell_reportsError() {
        List<List<Integer>> grid = mutableCopy(EASY_GRID);
        grid.get(0).set(2, 9); // wrong: SOLVED_GRID has 4 here

        ValidationResponse response = service.validatePuzzle(
                new BoardRequest(grid, SOLVED_GRID, null, null));

        assertFalse(response.isValid());
        List<Coordinate> errors = response.errors();
        assertEquals(1, errors.size());
        assertTrue(errors.contains(new Coordinate(0, 2)));
    }

    @Test
    void validatePuzzle_withSolution_noDuplicateButWrong_reportsError() {
        // This is the key case: no duplicates but cell doesn't match solution
        // Row 8, col 8: EASY_GRID has 9 (given). Let's build a grid with a non-duplicate wrong value
        // Use an empty cell and fill it with a plausible but wrong value
        List<List<Integer>> grid = mutableCopy(EASY_GRID);
        grid.get(0).set(2, 1); // SOLVED_GRID has 4; 1 is not a duplicate in row/col/block

        // Duplicate check alone would say valid; solution check says invalid
        ValidationResponse withoutSolution = service.validatePuzzle(new BoardRequest(grid));
        assertTrue(withoutSolution.isValid(), "Duplicate check sees no conflict");

        ValidationResponse withSolution = service.validatePuzzle(
                new BoardRequest(grid, SOLVED_GRID, null, null));
        assertFalse(withSolution.isValid(), "Solution check catches the wrong digit");
    }

    @Test
    void validatePuzzle_withSolution_solvedBoard_isSolved() {
        ValidationResponse response = service.validatePuzzle(
                new BoardRequest(SOLVED_GRID, SOLVED_GRID, null, null));

        assertTrue(response.isValid());
        assertTrue(response.isSolved());
        assertTrue(response.errors().isEmpty());
    }

    @Test
    void validatePuzzle_withSolution_multipleWrongCells_reportsAllErrors() {
        List<List<Integer>> grid = mutableCopy(EASY_GRID);
        grid.get(0).set(2, 9); // SOLVED_GRID has 4 here
        grid.get(0).set(3, 1); // SOLVED_GRID has 6 here

        ValidationResponse response = service.validatePuzzle(
                new BoardRequest(grid, SOLVED_GRID, null, null));

        assertFalse(response.isValid());
        List<Coordinate> errors = response.errors();
        assertTrue(errors.contains(new Coordinate(0, 2)), "Cell (0,2) should be an error");
        assertTrue(errors.contains(new Coordinate(0, 3)), "Cell (0,3) should be an error");
    }

    @Test
    void validatePuzzle_withSolution_partiallyFilledAllCorrect_notSolved() {
        // All filled cells match the solution but empty cells remain — valid but not solved
        ValidationResponse response = service.validatePuzzle(
                new BoardRequest(EASY_GRID, SOLVED_GRID, null, null));

        assertTrue(response.isValid());
        assertFalse(response.isSolved(), "Board still has empty cells so isSolved must be false");
        assertTrue(response.errors().isEmpty());
    }

    @Test
    void getHint_withMinRank_skipsStrategiesBelowThreshold() {
        SudokuServiceImpl fullService = new SudokuServiceImpl(List.of(
                new FullHouseStrategy(),    // rank 10
                new NakedSingleStrategy(),  // rank 20
                new NakedPairStrategy()     // rank 30
        ));
        // minRank 20 should skip FullHouse (rank 10) even if it would fire
        Optional<HintResponse> hint = fullService.getHint(
                new BoardRequest(EASY_GRID, 20));

        // EASY_GRID has a Naked Single — it should be returned (rank 20 >= minRank 20)
        assertTrue(hint.isPresent());
        assertNotEquals("Full House", hint.get().techniqueName(),
                "Full House (rank 10) should have been skipped by minRank=20");
    }

    // ---- getHint tests ----

    @Test
    void getHint_easyGrid_returnsNakedSingle() {
        SudokuServiceImpl fullService = new SudokuServiceImpl(List.of(
                new FullHouseStrategy(),
                new NakedSingleStrategy(),
                new NakedPairStrategy()
        ));
        Optional<HintResponse> hint = fullService.getHint(new BoardRequest(EASY_GRID));

        assertTrue(hint.isPresent());
        assertEquals("Naked Single", hint.get().techniqueName());
        List<ActionableCell> solvedCells = hint.get().solvedCells();
        assertEquals(1, solvedCells.size());
        assertEquals(4, solvedCells.get(0).row());
        assertEquals(4, solvedCells.get(0).col());
        assertEquals(5, solvedCells.get(0).value());
    }

    @Test
    void getHint_solvedGrid_returnsEmpty() {
        SudokuServiceImpl fullService = new SudokuServiceImpl(List.of(
                new FullHouseStrategy(),
                new NakedSingleStrategy(),
                new NakedPairStrategy()
        ));
        Optional<HintResponse> hint = fullService.getHint(new BoardRequest(SOLVED_GRID));

        assertTrue(hint.isEmpty());
    }

    @Test
    void getHint_skipsHintWithNoActionableOutcome() {
        // A strategy that returns a hint with no eliminations and no solvedCells
        com.sudoku.puzzle.hint.HintStrategy uselessStrategy = new com.sudoku.puzzle.hint.HintStrategy() {
            public int getDifficultyRank() { return 10; }
            public com.sudoku.puzzle.hint.Difficulty getDifficulty() { return com.sudoku.puzzle.hint.Difficulty.EASY; }
            public String getName() { return "Useless"; }
            public String getSlug() { return "useless"; }
            public Optional<HintResponse> evaluate(com.sudoku.domain.Board board) {
                return Optional.of(new HintResponse(
                        "Useless", "useless", com.sudoku.puzzle.hint.Difficulty.EASY, 10,
                        "nudge", "focus", "reveal",
                        List.of(), List.of(), List.of(), List.of()
                ));
            }
        };
        SudokuServiceImpl fullService = new SudokuServiceImpl(List.of(
                uselessStrategy,
                new NakedSingleStrategy()
        ));
        Optional<HintResponse> hint = fullService.getHint(new BoardRequest(EASY_GRID));

        assertTrue(hint.isPresent());
        assertEquals("Naked Single", hint.get().techniqueName(), "Should skip useless hint and return Naked Single");
    }

    @Test
    void getHint_withExcludedRanks_skipsMatchingStrategy() {
        SudokuServiceImpl fullService = new SudokuServiceImpl(List.of(
                new NakedSingleStrategy(),   // rank 20
                new NakedPairStrategy()      // rank 30
        ));
        // Exclude Naked Single (rank 20); board has a naked single so without exclusion it would be first
        Optional<HintResponse> hint = fullService.getHint(
                new BoardRequest(EASY_GRID, null, List.of(20)));

        // With rank 20 excluded, it should fall through to NakedPair (rank 30) or return empty
        // The important assertion: the returned hint must NOT be Naked Single
        hint.ifPresent(h -> assertNotEquals("Naked Single", h.techniqueName(),
                "Naked Single should have been excluded"));
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
