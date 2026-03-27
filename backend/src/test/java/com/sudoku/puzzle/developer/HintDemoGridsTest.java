package com.sudoku.puzzle.developer;

import com.sudoku.domain.Board;
import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.HintResponse;
import com.sudoku.puzzle.hint.FullHouseStrategy;
import com.sudoku.puzzle.hint.NakedPairStrategy;
import com.sudoku.puzzle.hint.NakedSingleStrategy;
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
     * Simulates the DevResource autocomplete: repeatedly applies simpler strategies
     * (filling solvedCells) until none fire.
     */
    private List<List<Integer>> autocompleteSimpler(List<List<Integer>> grid,
            com.sudoku.puzzle.hint.HintStrategy... simpler) {
        int[][] work = to2dArray(grid);
        boolean progress = true;
        while (progress) {
            progress = false;
            Board board = Board.fromGrid(toImmutableList(work));
            board.calculateAllCandidates();
            for (var strategy : simpler) {
                Optional<HintResponse> hint = strategy.evaluate(board);
                if (hint.isPresent() && hint.get().solvedCells() != null && !hint.get().solvedCells().isEmpty()) {
                    for (ActionableCell cell : hint.get().solvedCells()) {
                        work[cell.row()][cell.col()] = cell.value();
                    }
                    progress = true;
                    break;
                }
            }
        }
        return toImmutableList(work);
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
