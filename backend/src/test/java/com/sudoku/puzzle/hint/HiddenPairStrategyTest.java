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

// @spec HE-BE-016, HE-API-001, HE-API-002, HE-API-003, HE-API-004, HE-API-005, HE-API-006
class HiddenPairStrategyTest {

    private HiddenPairStrategy strategy;

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
        strategy = new HiddenPairStrategy();
    }

    @Test
    void hiddenPair_syntheticRowPair_returnsHint() {
        Board board = Board.fromGrid(Grid.of((EASY_GRID)));
        board.calculateAllCandidates();

        // Force a hidden pair of digits 2 and 6 in row 0:
        // Only cells (0,2) and (0,5) contain 2 and 6; other empty cells do not.
        // The pair cells have extra candidates (4 and 8 respectively) making it hidden.
        board.getCell(0, 2).setCandidates(Set.of(2, 4, 6));
        board.getCell(0, 3).setCandidates(Set.of(4, 8));
        board.getCell(0, 5).setCandidates(Set.of(2, 6, 8));
        board.getCell(0, 6).setCandidates(Set.of(4, 8));
        board.getCell(0, 7).setCandidates(Set.of(4, 8));
        board.getCell(0, 8).setCandidates(Set.of(4, 8));

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();

        List<Coordinate> highlights = hint.highlightCells();
        assertTrue(highlights.contains(new Coordinate(0, 2)));
        assertTrue(highlights.contains(new Coordinate(0, 5)));

        List<CoordinateCandidate> elims = hint.eliminatedCandidates();
        assertFalse(elims.isEmpty());
        // Eliminations must be from the pair cells only, for candidates other than 2 and 6
        for (CoordinateCandidate e : elims) {
            assertTrue(e.value() != 2 && e.value() != 6,
                    "Eliminated candidates must not be the hidden-pair digits");
        }

        assertTrue(hint.solvedCells().isEmpty());
    }

    @Test
    void hiddenPair_solvedBoard_returnsEmpty() {
        Board board = Board.fromGrid(Grid.of((SOLVED_GRID)));
        board.calculateAllCandidates();

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isEmpty());
    }

    @Test
    void hiddenPair_markdownSlug_and_difficulty() {
        Board board = Board.fromGrid(Grid.of((EASY_GRID)));
        board.calculateAllCandidates();
        board.getCell(0, 2).setCandidates(Set.of(2, 4, 6));
        board.getCell(0, 3).setCandidates(Set.of(4, 8));
        board.getCell(0, 5).setCandidates(Set.of(2, 6, 8));
        board.getCell(0, 6).setCandidates(Set.of(4, 8));
        board.getCell(0, 7).setCandidates(Set.of(4, 8));
        board.getCell(0, 8).setCandidates(Set.of(4, 8));

        HintResponse hint = strategy.evaluate(board).orElseThrow();
        assertEquals("hidden-pair", hint.markdownSlug());
        assertEquals(Difficulty.MEDIUM, hint.difficulty());
        assertEquals(70, strategy.getDifficultyRank());
    }
}
