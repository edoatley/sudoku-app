package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.dto.CoordinateCandidate;
import com.sudoku.dto.HintResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SwordfishStrategyTest {

    private SwordfishStrategy strategy;

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

    private static final List<List<Integer>> BLANK_GRID = List.of(
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

    @BeforeEach
    void setUp() {
        strategy = new SwordfishStrategy();
    }

    @Test
    void swordfish_rowBased_returnsHint() {
        Board board = Board.fromGrid(BLANK_GRID);
        clearAllCandidates(board);

        // Digit 7 spans exactly 3 columns {1, 4, 7} across rows 0, 3, 6 — Swordfish
        board.getCell(0, 1).setCandidates(Set.of(7, 2));
        board.getCell(0, 4).setCandidates(Set.of(7, 3));
        board.getCell(3, 1).setCandidates(Set.of(7, 5));
        board.getCell(3, 7).setCandidates(Set.of(7, 6));
        board.getCell(6, 4).setCandidates(Set.of(7, 8));
        board.getCell(6, 7).setCandidates(Set.of(7, 9));
        // Elimination targets: digit 7 in columns 1, 4, 7 outside rows 0, 3, 6
        board.getCell(1, 1).setCandidates(Set.of(7, 4));
        board.getCell(5, 4).setCandidates(Set.of(7, 3));
        board.getCell(8, 7).setCandidates(Set.of(7, 2));

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();
        assertEquals("swordfish", hint.markdownSlug());

        List<CoordinateCandidate> elims = hint.eliminatedCandidates();
        assertFalse(elims.isEmpty());
        for (CoordinateCandidate e : elims) {
            assertEquals(7, e.value());
            assertTrue(e.row() != 0 && e.row() != 3 && e.row() != 6,
                    "Eliminations must not be in the Swordfish rows");
        }

        assertTrue(hint.solvedCells().isEmpty());
    }

    @Test
    void swordfish_columnBased_returnsHint() {
        Board board = Board.fromGrid(BLANK_GRID);
        clearAllCandidates(board);

        // Digit 4 spans exactly 3 rows {2, 5, 8} across columns 0, 3, 6 — Swordfish
        board.getCell(2, 0).setCandidates(Set.of(4, 1));
        board.getCell(2, 3).setCandidates(Set.of(4, 2));
        board.getCell(5, 3).setCandidates(Set.of(4, 7));
        board.getCell(5, 6).setCandidates(Set.of(4, 9));
        board.getCell(8, 0).setCandidates(Set.of(4, 6));
        board.getCell(8, 6).setCandidates(Set.of(4, 3));
        // Elimination targets: digit 4 in rows 2, 5, 8 outside columns 0, 3, 6
        board.getCell(2, 7).setCandidates(Set.of(4, 5));
        board.getCell(5, 1).setCandidates(Set.of(4, 8));
        board.getCell(8, 4).setCandidates(Set.of(4, 2));

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();
        assertEquals("swordfish", hint.markdownSlug());

        List<CoordinateCandidate> elims = hint.eliminatedCandidates();
        assertFalse(elims.isEmpty());
        for (CoordinateCandidate e : elims) {
            assertEquals(4, e.value());
            assertTrue(e.col() != 0 && e.col() != 3 && e.col() != 6,
                    "Eliminations must not be in the Swordfish columns");
        }
    }

    @Test
    void swordfish_solvedBoard_returnsEmpty() {
        Board board = Board.fromGrid(SOLVED_GRID);
        board.calculateAllCandidates();

        assertTrue(strategy.evaluate(board).isEmpty());
    }

    @Test
    void swordfish_markdownSlug_and_difficulty() {
        Board board = Board.fromGrid(BLANK_GRID);
        clearAllCandidates(board);
        board.getCell(0, 1).setCandidates(Set.of(7, 2));
        board.getCell(0, 4).setCandidates(Set.of(7, 3));
        board.getCell(3, 1).setCandidates(Set.of(7, 5));
        board.getCell(3, 7).setCandidates(Set.of(7, 6));
        board.getCell(6, 4).setCandidates(Set.of(7, 8));
        board.getCell(6, 7).setCandidates(Set.of(7, 9));
        board.getCell(1, 1).setCandidates(Set.of(7, 4));

        HintResponse hint = strategy.evaluate(board).orElseThrow();
        assertEquals("swordfish", hint.markdownSlug());
        assertEquals("hard", hint.difficulty());
        assertEquals(100, strategy.getDifficultyRank());
    }

    private void clearAllCandidates(Board board) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                board.getCell(r, c).setCandidates(Set.of());
            }
        }
    }
}
