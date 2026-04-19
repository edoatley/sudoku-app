package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Grid;
import com.sudoku.dto.CoordinateCandidate;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import org.junit.jupiter.api.BeforeEach;
import com.sudoku.puzzle.hint.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// @spec HE-BE-018, HE-API-001, HE-API-002, HE-API-003, HE-API-004, HE-API-005, HE-API-006
class XWingStrategyTest {

    private XWingStrategy strategy;

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

    // A mostly-empty grid used as a blank canvas for synthetic candidate setup
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
        strategy = new XWingStrategy();
    }

    @Test
    void xWing_rowBased_returnsHint() {
        Board board = Board.fromGrid(Grid.of((BLANK_GRID)));
        // Clear all candidates first
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                board.getCell(r, c).setCandidates(Set.of());
            }
        }
        // Digit 5 is a candidate in exactly columns 2 and 6 in rows 1 and 4 (X-Wing)
        board.getCell(1, 2).setCandidates(Set.of(5, 3));
        board.getCell(1, 6).setCandidates(Set.of(5, 7));
        board.getCell(4, 2).setCandidates(Set.of(5, 8));
        board.getCell(4, 6).setCandidates(Set.of(5, 1));
        // Digit 5 also appears in column 2 and column 6 in other rows (elimination targets)
        board.getCell(0, 2).setCandidates(Set.of(5, 2));
        board.getCell(7, 6).setCandidates(Set.of(5, 4));

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();
        assertEquals("x-wing", hint.markdownSlug());

        List<Coordinate> highlights = hint.highlightCells();
        assertEquals(4, highlights.size());
        assertTrue(highlights.contains(new Coordinate(1, 2)));
        assertTrue(highlights.contains(new Coordinate(1, 6)));
        assertTrue(highlights.contains(new Coordinate(4, 2)));
        assertTrue(highlights.contains(new Coordinate(4, 6)));

        List<CoordinateCandidate> elims = hint.eliminatedCandidates();
        assertFalse(elims.isEmpty());
        for (CoordinateCandidate e : elims) {
            assertEquals(5, e.value());
            assertTrue(e.row() != 1 && e.row() != 4, "Eliminations must not be in the X-Wing rows");
        }

        assertTrue(hint.solvedCells().isEmpty());
    }

    @Test
    void xWing_columnBased_returnsHint() {
        Board board = Board.fromGrid(Grid.of((BLANK_GRID)));
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                board.getCell(r, c).setCandidates(Set.of());
            }
        }
        // Digit 3 is a candidate in exactly rows 0 and 5 in columns 3 and 7 (X-Wing)
        board.getCell(0, 3).setCandidates(Set.of(3, 6));
        board.getCell(0, 7).setCandidates(Set.of(3, 9));
        board.getCell(5, 3).setCandidates(Set.of(3, 2));
        board.getCell(5, 7).setCandidates(Set.of(3, 4));
        // Digit 3 also appears in rows 0 and 5 in other columns (elimination targets)
        board.getCell(0, 1).setCandidates(Set.of(3, 8));
        board.getCell(5, 5).setCandidates(Set.of(3, 7));

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();
        assertEquals("x-wing", hint.markdownSlug());

        List<CoordinateCandidate> elims = hint.eliminatedCandidates();
        assertFalse(elims.isEmpty());
        for (CoordinateCandidate e : elims) {
            assertEquals(3, e.value());
            assertTrue(e.col() != 3 && e.col() != 7, "Eliminations must not be in the X-Wing columns");
        }
    }

    @Test
    void xWing_solvedBoard_returnsEmpty() {
        Board board = Board.fromGrid(Grid.of((SOLVED_GRID)));
        board.calculateAllCandidates();

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isEmpty());
    }

    @Test
    void xWing_markdownSlug_and_difficulty() {
        Board board = Board.fromGrid(Grid.of((BLANK_GRID)));
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                board.getCell(r, c).setCandidates(Set.of());
            }
        }
        board.getCell(1, 2).setCandidates(Set.of(5, 3));
        board.getCell(1, 6).setCandidates(Set.of(5, 7));
        board.getCell(4, 2).setCandidates(Set.of(5, 8));
        board.getCell(4, 6).setCandidates(Set.of(5, 1));
        board.getCell(0, 2).setCandidates(Set.of(5, 2));

        HintResponse hint = strategy.evaluate(board).orElseThrow();
        assertEquals("x-wing", hint.markdownSlug());
        assertEquals(Difficulty.HARD, hint.difficulty());
        assertEquals(90, strategy.getDifficultyRank());
    }
}
