package com.sudoku.puzzle.developer;

import com.sudoku.domain.Board;
import com.sudoku.dto.HintResponse;
import com.sudoku.puzzle.hint.FullHouseStrategy;
import com.sudoku.puzzle.hint.HiddenPairStrategy;
import com.sudoku.puzzle.hint.HiddenSingleStrategy;
import com.sudoku.puzzle.hint.HiddenTripleStrategy;
import com.sudoku.puzzle.hint.NakedPairStrategy;
import com.sudoku.puzzle.hint.NakedSingleStrategy;
import com.sudoku.puzzle.hint.NakedTripleStrategy;
import com.sudoku.puzzle.hint.PointingPairStrategy;
import com.sudoku.puzzle.hint.SwordfishStrategy;
import com.sudoku.puzzle.hint.XWingStrategy;
import com.sudoku.puzzle.hint.YWingStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests: each demo grid must eventually fire the named strategy
 * (either directly on the raw grid, or after autocompleting simpler strategies
 * as the DevResource does at runtime).
 */
// @spec PG-DEV-002, PG-DEV-003
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
    void nakedPair_demoGrid_strategyFiresOnRawGrid() {
        Board board = boardFor("naked-pair");

        Optional<HintResponse> hint = new NakedPairStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "NakedPairStrategy must fire on the raw naked-pair demo grid");
        assertEquals("naked-pair", hint.get().markdownSlug());
        assertFalse(hint.get().eliminatedCandidates().isEmpty(), "Naked pair must produce eliminations");
    }

    @Test
    void nakedPair_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("naked-pair"));
    }

    // ---- hidden-single ----

    @Test
    void hiddenSingle_demoGrid_strategyFiresOnRawGrid() {
        Board board = boardFor("hidden-single");

        Optional<HintResponse> hint = new HiddenSingleStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "HiddenSingleStrategy must fire on the raw hidden-single demo grid");
        assertEquals("hidden-single", hint.get().markdownSlug());
        assertFalse(hint.get().solvedCells().isEmpty(), "Hidden single must produce a solved cell");
    }

    @Test
    void hiddenSingle_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("hidden-single"));
    }

    // ---- pointing-pair ----

    @Test
    void pointingPair_demoGrid_strategyFiresOnRawGrid() {
        Board board = boardFor("pointing-pair");

        Optional<HintResponse> hint = new PointingPairStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "PointingPairStrategy must fire on the raw pointing-pair demo grid");
        assertEquals("pointing-pair", hint.get().markdownSlug());
        assertFalse(hint.get().eliminatedCandidates().isEmpty(), "Pointing pair must produce candidate eliminations");
    }

    @Test
    void pointingPair_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("pointing-pair"));
    }

    // ---- naked-triple ----

    @Test
    void nakedTriple_demoGrid_strategyFiresOnRawGrid() {
        Board board = boardFor("naked-triple");

        Optional<HintResponse> hint = new NakedTripleStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "NakedTripleStrategy must fire on the raw naked-triple demo grid");
        assertEquals("naked-triple", hint.get().markdownSlug());
        assertFalse(hint.get().eliminatedCandidates().isEmpty(), "Naked triple must produce candidate eliminations");
    }

    @Test
    void nakedTriple_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("naked-triple"));
    }

    // ---- hidden-pair ----

    @Test
    void hiddenPair_demoGrid_strategyFiresOnRawGrid() {
        Board board = boardFor("hidden-pair");

        Optional<HintResponse> hint = new HiddenPairStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "HiddenPairStrategy must fire on the raw hidden-pair demo grid");
        assertEquals("hidden-pair", hint.get().markdownSlug());
        assertFalse(hint.get().eliminatedCandidates().isEmpty(), "Hidden pair must produce eliminations");
    }

    @Test
    void hiddenPair_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("hidden-pair"));
    }

    // ---- hidden-triple ----

    @Test
    void hiddenTriple_demoGrid_strategyFiresOnRawGrid() {
        Board board = boardFor("hidden-triple");

        Optional<HintResponse> hint = new HiddenTripleStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "HiddenTripleStrategy must fire on the raw hidden-triple demo grid");
        assertEquals("hidden-triple", hint.get().markdownSlug());
        assertFalse(hint.get().eliminatedCandidates().isEmpty(), "Hidden triple must produce eliminations");
    }

    @Test
    void hiddenTriple_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("hidden-triple"));
    }

    // ---- x-wing ----

    @Test
    void xWing_demoGrid_strategyFiresOnRawGrid() {
        Board board = boardFor("x-wing");

        assertFalse(hasNoEmptyCells(board), "X-Wing demo grid must have empty cells");

        Optional<HintResponse> hint = new XWingStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "XWingStrategy must fire on the raw x-wing demo grid");
        assertEquals("x-wing", hint.get().markdownSlug());
        assertFalse(hint.get().eliminatedCandidates().isEmpty(), "X-Wing must produce eliminations");
    }

    @Test
    void xWing_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("x-wing"));
    }

    // ---- swordfish ----

    @Test
    void swordfish_demoGrid_strategyFiresOnRawGrid() {
        Board board = boardFor("swordfish");

        assertFalse(hasNoEmptyCells(board), "Swordfish demo grid must have empty cells");

        Optional<HintResponse> hint = new SwordfishStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "SwordfishStrategy must fire on the raw swordfish demo grid");
        assertEquals("swordfish", hint.get().markdownSlug());
        assertFalse(hint.get().eliminatedCandidates().isEmpty(), "Swordfish must produce eliminations");
    }

    @Test
    void swordfish_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("swordfish"));
    }

    // ---- y-wing ----

    @Test
    void yWing_demoGrid_strategyFiresOnRawGrid() {
        Board board = boardFor("y-wing");

        assertFalse(hasNoEmptyCells(board), "Y-Wing demo grid must have empty cells");

        Optional<HintResponse> hint = new YWingStrategy().evaluate(board);

        assertTrue(hint.isPresent(), "YWingStrategy must fire on the raw y-wing demo grid");
        assertEquals("y-wing", hint.get().markdownSlug());
        assertFalse(hint.get().eliminatedCandidates().isEmpty(), "Y-Wing must produce eliminations");
    }

    @Test
    void yWing_demoGrid_isValidSudoku() {
        assertValidPartialSudoku(HintDemoGrids.forSlug("y-wing"));
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
        com.sudoku.domain.Grid grid = HintDemoGrids.forSlug(slug);
        assertNotNull(grid, "Grid must be registered for '" + slug + "'");
        Board board = Board.fromGrid(grid);
        board.calculateAllCandidates();
        return board;
    }

    /**
     * Asserts the grid is a well-formed 9×9 board with no duplicate digits
     * in any row, column, or 3×3 block (ignoring empty cells).
     */
    private void assertValidPartialSudoku(com.sudoku.domain.Grid grid) {
        assertNotNull(grid);
        assertEquals(9, grid.size(), "Grid must have 9 rows");
        for (int r = 0; r < 9; r++) {
            assertEquals(9, grid.row(r).size(), "Row " + r + " must have 9 columns");
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

    private boolean hasNoEmptyCells(Board board) {
        for (int r = 0; r < 9; r++) {
            for (com.sudoku.domain.Cell cell : board.getRow(r)) {
                if (cell.isEmpty()) return false;
            }
        }
        return true;
    }

}
