package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Grid;
import com.sudoku.puzzle.web.ActionableCell;
import com.sudoku.puzzle.web.HintResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// @spec HE-BE-010, HE-API-001, HE-API-002, HE-API-003, HE-API-004, HE-API-005, HE-API-006
class FullHouseStrategyTest {

    private FullHouseStrategy strategy;

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
        strategy = new FullHouseStrategy();
    }

    @Test
    void fullHouse_rowWithOneEmptyCell_returnsHint() {
        // SOLVED_GRID with (0,8) set to 0 → row 0 has exactly 1 empty cell, missing digit = 2
        List<List<Integer>> grid = mutableCopy(SOLVED_GRID);
        grid.get(0).set(8, 0);

        Board board = Board.fromGrid(Grid.of((grid)));
        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();
        assertEquals(1, hint.solvedCells().size());
        ActionableCell cell = hint.solvedCells().get(0);
        assertEquals(0, cell.row());
        assertEquals(8, cell.col());
        assertEquals(2, cell.value());
    }

    @Test
    void fullHouse_columnFullHouse_detected() {
        // SOLVED_GRID with (3,0)=0 AND (3,4)=0
        // row 3 has 2 empties (no Full House for row), col 0 has 1 empty → detects column Full House at (3,0)
        List<List<Integer>> grid = mutableCopy(SOLVED_GRID);
        grid.get(3).set(0, 0); // clear (3,0) — was 8
        grid.get(3).set(4, 0); // clear (3,4) — was 6

        Board board = Board.fromGrid(Grid.of((grid)));
        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isPresent());
        HintResponse hint = result.get();
        assertEquals(1, hint.solvedCells().size());
        ActionableCell cell = hint.solvedCells().get(0);
        // Column 0 has exactly 1 empty: (3,0). Missing digit is 8.
        assertEquals(3, cell.row());
        assertEquals(0, cell.col());
        assertEquals(8, cell.value());
    }

    @Test
    void fullHouse_noFullHouses_returnsEmpty() {
        Board board = Board.fromGrid(Grid.of((EASY_GRID)));
        Optional<HintResponse> result = strategy.evaluate(board);

        assertTrue(result.isEmpty());
    }

    @Test
    void fullHouse_markdownSlug_and_difficulty() {
        List<List<Integer>> grid = mutableCopy(SOLVED_GRID);
        grid.get(0).set(8, 0);
        Board board = Board.fromGrid(Grid.of((grid)));

        HintResponse hint = strategy.evaluate(board).orElseThrow();
        assertEquals("full-house", hint.markdownSlug());
        assertEquals(Difficulty.EASY, hint.difficulty());
    }

    private List<List<Integer>> mutableCopy(List<List<Integer>> original) {
        List<List<Integer>> copy = new ArrayList<>();
        for (List<Integer> row : original) {
            copy.add(new ArrayList<>(row));
        }
        return copy;
    }
}
