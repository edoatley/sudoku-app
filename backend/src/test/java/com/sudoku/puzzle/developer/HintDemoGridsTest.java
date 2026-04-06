package com.sudoku.puzzle.developer;

import com.sudoku.domain.Board;
import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.CandidateElimination;
import com.sudoku.dto.HintResponse;
import com.sudoku.puzzle.hint.FullHouseStrategy;
import com.sudoku.puzzle.hint.HiddenSingleStrategy;
import com.sudoku.puzzle.hint.NakedPairStrategy;
import com.sudoku.puzzle.hint.NakedSingleStrategy;
import com.sudoku.puzzle.hint.HiddenPairStrategy;
import com.sudoku.puzzle.hint.NakedTripleStrategy;
import com.sudoku.puzzle.hint.PointingPairStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests: each demo grid must eventually fire the named strategy
 * (either directly on the raw grid, or after autocompleting simpler strategies
 * as the DevResource does at runtime).
 */
class HintDemoGridsTest {

    // ---- full-house ----

    @Test
    void fullHouse_demoGrid_strategyFires() {
        Board board = boardFor("full-house");

        Optional<HintResponse> hint = new FullHouseStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "FullHouseStrategy must fire on the full-house demo grid");
        assertEquals("full-house", hint.get().markdownSlug());
    }

    @Test
    void fullHouse_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("full-house"));
    }

    // ---- naked-single ----

    @Test
    void nakedSingle_demoGrid_strategyFires() {
        Board board = boardFor("naked-single");

        Optional<HintResponse> hint = new NakedSingleStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "NakedSingleStrategy must fire on the naked-single demo grid");
        assertEquals("naked-single", hint.get().markdownSlug());
    }

    @Test
    void nakedSingle_demoGrid_noFullHouseFires() {
        Board board = boardFor("naked-single");

        Optional<HintResponse> hint = new FullHouseStrategy().evaluate(board);
        assertTrue(hint.isEmpty(),
                "FullHouseStrategy must NOT fire on the naked-single demo grid (it would take priority over NakedSingle)");
    }

    @Test
    void nakedSingle_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("naked-single"));
    }

    // ---- naked-pair ----

    @Test
    void nakedPair_demoGrid_strategyFiresAfterAutocomplete() {
        // The raw naked-pair grid requires FullHouse/NakedSingle autocomplete first
        // (matching what DevResource does before serving the grid).
        List<List<Integer>> autocompleted = autocompleteSimpler(
                HintDemoGrids.forSlug("naked-pair"),
                new FullHouseStrategy(), new NakedSingleStrategy());

        Board board = Board.fromGrid(autocompleted);
        board.calculateAllCandidates();

        Optional<HintResponse> hint = new NakedPairStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "NakedPairStrategy must fire after autocomplete of the naked-pair demo grid");
        assertEquals("naked-pair", hint.get().markdownSlug());
        assertFalse(hint.get().eliminatedCandidates().isEmpty(), "Naked pair must produce eliminations");
    }

    @Test
    void nakedPair_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("naked-pair"));
    }

    // ---- hidden-single ----

    @Test
    void hiddenSingle_demoGrid_strategyFiresAfterAutocomplete() {
        List<List<Integer>> autocompleted = autocompleteSimpler(
                HintDemoGrids.forSlug("hidden-single"),
                new FullHouseStrategy(), new NakedSingleStrategy());

        Board board = Board.fromGrid(autocompleted);
        board.calculateAllCandidates();

        Optional<HintResponse> hint = new HiddenSingleStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "HiddenSingleStrategy must fire after autocomplete of the hidden-single demo grid");
        assertEquals("hidden-single", hint.get().markdownSlug());
        assertFalse(hint.get().solvedCells().isEmpty(), "Hidden single must produce a solved cell");
    }

    @Test
    void hiddenSingle_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("hidden-single"));
    }

    // ---- pointing-pair ----

    @Test
    void pointingPair_demoGrid_strategyFiresAfterAutocomplete() {
        List<List<Integer>> autocompleted = autocompleteSimpler(
                HintDemoGrids.forSlug("pointing-pair"),
                new FullHouseStrategy(), new NakedSingleStrategy(),
                new NakedPairStrategy(), new HiddenSingleStrategy());

        Board board = Board.fromGrid(autocompleted);
        board.calculateAllCandidates();

        Optional<HintResponse> hint = new PointingPairStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "PointingPairStrategy must fire after autocomplete of the pointing-pair demo grid");
        assertEquals("pointing-pair", hint.get().markdownSlug());
        assertFalse(hint.get().eliminatedCandidates().isEmpty(), "Pointing pair must produce candidate eliminations");
    }

    @Test
    void pointingPair_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("pointing-pair"));
    }

    // ---- naked-triple ----

    @Test
    void nakedTriple_demoGrid_strategyFiresAfterAutocomplete() {
        // Autocomplete includes PointingPair (rank 50) so its eliminations are applied first,
        // ensuring PP is exhausted before NT is checked — matching DevResource behaviour.
        Board board = autocompleteToBoard(
                HintDemoGrids.forSlug("naked-triple"),
                new FullHouseStrategy(), new NakedSingleStrategy(),
                new NakedPairStrategy(), new HiddenSingleStrategy(),
                new PointingPairStrategy());

        // Verify PointingPair does not fire on the autocompleted board (it must be exhausted)
        Optional<HintResponse> ppHint = new PointingPairStrategy().evaluate(board);
        assertTrue(ppHint.isEmpty(),
                "PointingPairStrategy must NOT fire on the autocompleted naked-triple demo grid " +
                "(it would take priority over NakedTriple at runtime)");

        Optional<HintResponse> hint = new NakedTripleStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "NakedTripleStrategy must fire after autocomplete of the naked-triple demo grid");
        assertEquals("naked-triple", hint.get().markdownSlug());
        assertFalse(hint.get().eliminatedCandidates().isEmpty(), "Naked triple must produce candidate eliminations");
    }

    @Test
    void nakedTriple_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("naked-triple"));
    }

    // ---- hidden-pair ----

    @Test
    void hiddenPair_demoGrid_strategyFiresAfterAutocomplete() {
        Board board = autocompleteToBoard(
                HintDemoGrids.forSlug("hidden-pair"),
                new FullHouseStrategy(), new NakedSingleStrategy(),
                new NakedPairStrategy(), new HiddenSingleStrategy(),
                new PointingPairStrategy(), new NakedTripleStrategy());

        Optional<HintResponse> hint = new HiddenPairStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "HiddenPairStrategy must fire after autocomplete of the hidden-pair demo grid");
        assertEquals("hidden-pair", hint.get().markdownSlug());
        assertFalse(hint.get().eliminatedCandidates().isEmpty(), "Hidden pair must produce eliminations");
    }

    @Test
    void hiddenPair_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("hidden-pair"));
    }

    // ---- slugs coverage ----

    @Test
    void allSlugs_haveRegisteredGrids() {
        for (String slug : HintDemoGrids.slugs()) {
            assertNotNull(HintDemoGrids.forSlug(slug), "Slug '" + slug + "' has a null grid");
        }
    }

    @Test
    void unknownSlug_returnsNull() {
        assertNull(HintDemoGrids.forSlug("does-not-exist"));
    }

    // ---- helpers ----

    private Board boardFor(String slug) {
        List<List<Integer>> grid = HintDemoGrids.forSlug(slug);
        assertNotNull(grid, "Grid must be registered for '" + slug + "'");
        Board board = Board.fromGrid(grid);
        board.calculateAllCandidates();
        return board;
    }

    /**
     * Simulates the DevResource autocomplete: applies simpler strategies (filling solvedCells
     * AND applying eliminatedCandidates) on a persistent board until none fire.
     * Returns the autocompleted grid as an immutable list (for grid-based assertions).
     */
    private List<List<Integer>> autocompleteSimpler(List<List<Integer>> grid,
            com.sudoku.puzzle.hint.HintStrategy... simpler) {
        int[][] work = to2dArray(grid);
        autocompleteToBoard(grid, work, simpler);
        return toImmutableList(work);
    }

    /**
     * Like {@link #autocompleteSimpler} but returns the live Board so callers can
     * evaluate further strategies against the fully-resolved candidate state.
     */
    private Board autocompleteToBoard(List<List<Integer>> grid,
            com.sudoku.puzzle.hint.HintStrategy... simpler) {
        int[][] work = to2dArray(grid);
        return autocompleteToBoard(grid, work, simpler);
    }

    private Board autocompleteToBoard(List<List<Integer>> grid, int[][] work,
            com.sudoku.puzzle.hint.HintStrategy... simpler) {
        Board board = Board.fromGrid(toImmutableList(work));
        board.calculateAllCandidates();
        boolean progress = true;
        while (progress) {
            progress = false;
            for (var strategy : simpler) {
                Optional<HintResponse> hint = strategy.evaluate(board);
                if (hint.isPresent()) {
                    HintResponse h = hint.get();
                    boolean changed = false;
                    if (h.solvedCells() != null && !h.solvedCells().isEmpty()) {
                        for (ActionableCell cell : h.solvedCells()) {
                            work[cell.row()][cell.col()] = cell.value();
                            board.getCell(cell.row(), cell.col()).setValue(cell.value());
                            changed = true;
                        }
                        board.calculateAllCandidates();
                    }
                    if (h.eliminatedCandidates() != null && !h.eliminatedCandidates().isEmpty()) {
                        for (CandidateElimination elim : h.eliminatedCandidates()) {
                            board.getCell(elim.row(), elim.col()).removeCandidate(elim.value());
                            changed = true;
                        }
                    }
                    if (changed) {
                        progress = true;
                        break;
                    }
                }
            }
        }
        return board;
    }

    /**
     * Asserts the grid is a well-formed 9×9 board with no duplicate digits
     * in any row, column, or 3×3 block (ignoring empty cells).
     */
    private void assertValidPartialSudoku(List<List<Integer>> grid) {
        assertNotNull(grid);
        assertEquals(9, grid.size(), "Grid must have 9 rows");
        for (int r = 0; r < 9; r++) {
            assertEquals(9, grid.get(r).size(), "Row " + r + " must have 9 columns");
        }
        Board board = Board.fromGrid(grid);
        for (int i = 0; i < 9; i++) {
            assertNoDuplicates(board.getRow(i), "row " + i);
            assertNoDuplicates(board.getColumn(i), "col " + i);
            assertNoDuplicates(board.getBlock(i), "block " + i);
        }
    }

    private void assertNoDuplicates(List<com.sudoku.domain.Cell> unit, String label) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (com.sudoku.domain.Cell cell : unit) {
            if (!cell.isEmpty()) {
                assertTrue(seen.add(cell.value()),
                        "Duplicate digit " + cell.value() + " in " + label);
            }
        }
    }

    private int[][] to2dArray(List<List<Integer>> grid) {
        int[][] arr = new int[9][9];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                arr[r][c] = grid.get(r).get(c);
            }
        }
        return arr;
    }

    private List<List<Integer>> toImmutableList(int[][] arr) {
        List<List<Integer>> result = new ArrayList<>(9);
        for (int r = 0; r < 9; r++) {
            List<Integer> row = new ArrayList<>(9);
            for (int c = 0; c < 9; c++) {
                row.add(arr[r][c]);
            }
            result.add(List.copyOf(row));
        }
        return List.copyOf(result);
    }
}
