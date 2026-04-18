package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.HintResponse;
import org.junit.jupiter.api.BeforeEach;
import com.sudoku.puzzle.hint.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// @spec HE-BE-011, HE-API-001, HE-API-002, HE-API-003, HE-API-004, HE-API-005, HE-API-006
class NakedSingleStrategyTest {

    private NakedSingleStrategy strategy;

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

    @BeforeEach
    void setUp() {
        strategy = new NakedSingleStrategy();
    }

    @Test
    void nakedSingle_easyGrid_returnsFirstNakedSingle() {
        Board board = Board.fromGrid(EASY_GRID);
        board.calculateAllCandidates();

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();
        List<ActionableCell> solvedCells = hint.solvedCells();
        assertEquals(1, solvedCells.size());
        // First naked single in EASY_GRID is (4,4) with value 5
        assertEquals(4, solvedCells.get(0).row());
        assertEquals(4, solvedCells.get(0).col());
        assertEquals(5, solvedCells.get(0).value());
    }

    @Test
    void nakedSingle_emptyBoard_returnsEmpty() {
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
        Board board = Board.fromGrid(emptyGrid);
        board.calculateAllCandidates();

        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isEmpty());
    }

    @Test
    void nakedSingle_markdownSlug_and_difficulty() {
        Board board = Board.fromGrid(EASY_GRID);
        board.calculateAllCandidates();

        HintResponse hint = strategy.evaluate(board).orElseThrow();
        assertEquals("naked-single", hint.markdownSlug());
        assertEquals(Difficulty.EASY, hint.difficulty());
    }
}
