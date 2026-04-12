package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
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

class YWingStrategyTest {

    private YWingStrategy strategy;

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
        strategy = new YWingStrategy();
    }

    @Test
    void yWing_detection_returnsHint() {
        Board board = Board.fromGrid(BLANK_GRID);
        clearAllCandidates(board);

        // Pivot at (2,4) has {3,7}
        // Pincer 1 at (2,8) has {3,5} — shares 3 with pivot, has extra candidate 5
        // Pincer 2 at (6,4) has {7,5} — shares 7 with pivot, has extra candidate 5
        // Both pincers share C=5, so any cell visible to both (2,8) and (6,4) can have 5 eliminated.
        // (6,8) is visible to (2,8) via column 8, and to (6,4) via row 6 → elimination target.
        board.getCell(2, 4).setCandidates(Set.of(3, 7));  // pivot
        board.getCell(2, 8).setCandidates(Set.of(3, 5));  // pincer 1 — same row as pivot
        board.getCell(6, 4).setCandidates(Set.of(7, 5));  // pincer 2 — same col as pivot
        board.getCell(6, 8).setCandidates(Set.of(5, 2));  // elimination target: sees both pincers

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();
        assertEquals("y-wing", hint.markdownSlug());

        List<Coordinate> highlights = hint.highlightCells();
        assertEquals(3, highlights.size());
        assertTrue(highlights.contains(new Coordinate(2, 4)));

        List<CoordinateCandidate> elims = hint.eliminatedCandidates();
        assertFalse(elims.isEmpty());
        for (CoordinateCandidate e : elims) {
            assertEquals(5, e.value(), "Eliminated candidate must be the shared pincer digit C=5");
        }
        assertTrue(elims.stream().anyMatch(e -> e.row() == 6 && e.col() == 8));

        assertTrue(hint.solvedCells().isEmpty());
    }

    @Test
    void yWing_solvedBoard_returnsEmpty() {
        Board board = Board.fromGrid(SOLVED_GRID);
        board.calculateAllCandidates();

        assertTrue(strategy.evaluate(board).isEmpty());
    }

    @Test
    void yWing_markdownSlug_and_difficulty() {
        Board board = Board.fromGrid(BLANK_GRID);
        clearAllCandidates(board);
        board.getCell(2, 4).setCandidates(Set.of(3, 7));
        board.getCell(2, 8).setCandidates(Set.of(3, 5));
        board.getCell(6, 4).setCandidates(Set.of(7, 5));
        board.getCell(6, 8).setCandidates(Set.of(5, 2));

        HintResponse hint = strategy.evaluate(board).orElseThrow();
        assertEquals("y-wing", hint.markdownSlug());
        assertEquals(Difficulty.HARD, hint.difficulty());
        assertEquals(110, strategy.getDifficultyRank());
    }

    private void clearAllCandidates(Board board) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                board.getCell(r, c).setCandidates(Set.of());
            }
        }
    }
}
