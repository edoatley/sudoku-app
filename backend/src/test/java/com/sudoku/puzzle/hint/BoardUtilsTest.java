package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.sudoku.puzzle.hint.BoardUtils.candidateColumnsInRow;
import static com.sudoku.puzzle.hint.BoardUtils.candidateRowsInColumn;
import static com.sudoku.puzzle.hint.BoardUtils.isVisible;
import static org.junit.jupiter.api.Assertions.*;

class BoardUtilsTest {

    private Board board;

    // Minimal board with row 0 having digit 5 as a candidate in columns 2 and 7
    private static final List<List<Integer>> EMPTY_GRID = new ArrayList<>();

    static {
        for (int r = 0; r < 9; r++) {
            List<Integer> row = new ArrayList<>();
            for (int c = 0; c < 9; c++) {
                row.add(0);
            }
            EMPTY_GRID.add(row);
        }
    }

    @BeforeEach
    void setUp() {
        board = Board.fromGrid(EMPTY_GRID);
        board.calculateAllCandidates();
    }

    // --- candidateColumnsInRow ---

    @Test
    void candidateColumnsInRow_returnsColumnsContainingDigit() {
        // On a fully empty board every column in every row is a candidate for every digit
        List<Integer> cols = candidateColumnsInRow(board, 0, 5);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), cols);
    }

    @Test
    void candidateColumnsInRow_returnsAscendingOrder() {
        List<Integer> cols = candidateColumnsInRow(board, 3, 1);
        for (int i = 0; i < cols.size() - 1; i++) {
            assertTrue(cols.get(i) < cols.get(i + 1), "Columns must be in ascending order");
        }
    }

    @Test
    void candidateColumnsInRow_excludesFilledCells() {
        // Build a grid with digit 5 placed at (0,4) — the filled cell itself is not empty,
        // so candidateColumnsInRow must not include column 4 (it is not an empty cell).
        // Digit 5 is also removed from all other cells in row 0 by candidate calculation,
        // so the result for digit 5 in row 0 should be empty.
        List<List<Integer>> grid = new ArrayList<>();
        for (int r = 0; r < 9; r++) {
            List<Integer> row = new ArrayList<>();
            for (int c = 0; c < 9; c++) {
                row.add(r == 0 && c == 4 ? 5 : 0);
            }
            grid.add(row);
        }
        Board partialBoard = Board.fromGrid(grid);
        partialBoard.calculateAllCandidates();

        List<Integer> cols = candidateColumnsInRow(partialBoard, 0, 5);
        assertFalse(cols.contains(4), "Filled cell column should not be included");
        assertTrue(cols.isEmpty(), "Digit already placed in this row should have no remaining candidate columns");
    }

    // --- candidateRowsInColumn ---

    @Test
    void candidateRowsInColumn_returnsRowsContainingDigit() {
        List<Integer> rows = candidateRowsInColumn(board, 0, 3);
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8), rows);
    }

    @Test
    void candidateRowsInColumn_returnsAscendingOrder() {
        List<Integer> rows = candidateRowsInColumn(board, 5, 7);
        for (int i = 0; i < rows.size() - 1; i++) {
            assertTrue(rows.get(i) < rows.get(i + 1), "Rows must be in ascending order");
        }
    }

    @Test
    void candidateRowsInColumn_excludesFilledCells() {
        // Digit 7 placed at (3,0) — the filled cell is not empty (excluded directly),
        // and digit 7 is also eliminated from all other cells in column 0 by candidate
        // calculation, so the result for digit 7 in column 0 should be empty.
        List<List<Integer>> grid = new ArrayList<>();
        for (int r = 0; r < 9; r++) {
            List<Integer> row = new ArrayList<>();
            for (int c = 0; c < 9; c++) {
                row.add(r == 3 && c == 0 ? 7 : 0);
            }
            grid.add(row);
        }
        Board partialBoard = Board.fromGrid(grid);
        partialBoard.calculateAllCandidates();

        List<Integer> rows = candidateRowsInColumn(partialBoard, 0, 7);
        assertFalse(rows.contains(3), "Filled cell row should not be included");
        assertTrue(rows.isEmpty(), "Digit already placed in this column should have no remaining candidate rows");
    }

    // --- isVisible ---

    @Test
    void isVisible_sameRow() {
        Cell a = board.getCell(2, 1);
        Cell b = board.getCell(2, 7);
        assertTrue(isVisible(a, b));
    }

    @Test
    void isVisible_sameColumn() {
        Cell a = board.getCell(0, 5);
        Cell b = board.getCell(8, 5);
        assertTrue(isVisible(a, b));
    }

    @Test
    void isVisible_sameBlock() {
        Cell a = board.getCell(0, 0);
        Cell b = board.getCell(2, 2); // both in block 0
        assertTrue(isVisible(a, b));
    }

    @Test
    void isVisible_sameBlockDifferentRowAndColumn() {
        Cell a = board.getCell(3, 3);
        Cell b = board.getCell(5, 5); // both in block 4
        assertTrue(isVisible(a, b));
    }

    @Test
    void isVisible_notVisible() {
        Cell a = board.getCell(0, 0);
        Cell b = board.getCell(4, 4); // different row, column, and block
        assertFalse(isVisible(a, b));
    }

    @Test
    void isVisible_differentBlockSameNeitherRowNorColumn() {
        Cell a = board.getCell(0, 3);
        Cell b = board.getCell(3, 0); // block 0 vs block 3
        assertFalse(isVisible(a, b));
    }
}
