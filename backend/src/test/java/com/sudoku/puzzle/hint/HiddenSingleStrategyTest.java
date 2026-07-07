package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Grid;
import com.sudoku.puzzle.web.ActionableCell;
import com.sudoku.puzzle.web.Coordinate;
import com.sudoku.puzzle.web.HintResponse;
import org.junit.jupiter.api.BeforeEach;
import com.sudoku.puzzle.hint.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

// @spec HE-BE-013, HE-API-001, HE-API-002, HE-API-003, HE-API-004, HE-API-005, HE-API-006
class HiddenSingleStrategyTest {

    private HiddenSingleStrategy strategy;

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
        strategy = new HiddenSingleStrategy();
    }

    @Test
    void hiddenSingle_syntheticRowPattern_returnsHint() {
        Board board = Board.fromGrid(Grid.of((EASY_GRID)));
        board.calculateAllCandidates();

        // Row 0 empty cells: cols 2, 3, 5, 6, 7, 8.
        // Patch so digit 4 is a candidate only in (0,2) — a hidden single in row 0.
        // Every other digit must appear in ≥2 row-0 empty cells to avoid accidental hidden singles.
        // Digit counts: 1→{5,6,8}, 2→{3,5,7,8}, 3→{3,6,7}, 4→{2} only
        board.getCell(0, 2).setCandidates(Set.of(4));
        board.getCell(0, 3).setCandidates(Set.of(2, 3));
        board.getCell(0, 5).setCandidates(Set.of(1, 2));
        board.getCell(0, 6).setCandidates(Set.of(1, 3));
        board.getCell(0, 7).setCandidates(Set.of(2, 3));
        board.getCell(0, 8).setCandidates(Set.of(1, 2));

        // Rows 1-8 keep their calculateAllCandidates() results. Row 0 is scanned first,
        // and digit 4 is unique in row 0, so the strategy fires there before any other unit.

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();

        // Solved cell must be (0,2) with digit 4
        assertEquals(1, hint.solvedCells().size());
        ActionableCell solved = hint.solvedCells().get(0);
        assertEquals(0, solved.row());
        assertEquals(2, solved.col());
        assertEquals(4, solved.value());

        // Highlight must cover all 9 cells of row 0
        List<Coordinate> highlights = hint.highlightCells();
        assertEquals(9, highlights.size());
        for (int c = 0; c < 9; c++) {
            assertTrue(highlights.contains(new Coordinate(0, c)),
                    "Row 0 col " + c + " should be in highlightCells");
        }

        // Hidden Single makes no candidate eliminations
        assertTrue(hint.eliminatedCandidates().isEmpty());
    }

    @Test
    void hiddenSingle_solvedBoard_returnsEmpty() {
        Board board = Board.fromGrid(Grid.of((SOLVED_GRID)));
        board.calculateAllCandidates();

        assertTrue(strategy.evaluate(board).isEmpty());
    }

    @Test
    void hiddenSingle_metadata() {
        Board board = Board.fromGrid(Grid.of((EASY_GRID)));
        board.calculateAllCandidates();

        board.getCell(0, 2).setCandidates(Set.of(4));
        board.getCell(0, 3).setCandidates(Set.of(2, 3));
        board.getCell(0, 5).setCandidates(Set.of(1, 2));
        board.getCell(0, 6).setCandidates(Set.of(1, 3));
        board.getCell(0, 7).setCandidates(Set.of(2, 3));
        board.getCell(0, 8).setCandidates(Set.of(1, 2));

        assertEquals(40, strategy.getDifficultyRank());
        HintResponse hint = strategy.evaluate(board).orElseThrow();
        assertEquals("hidden-single", hint.markdownSlug());
        assertEquals(Difficulty.EASY, hint.difficulty());
    }
}
