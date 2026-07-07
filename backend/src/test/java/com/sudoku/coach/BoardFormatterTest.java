package com.sudoku.coach;

import com.sudoku.coach.bedrock.BoardFormatter;
import com.sudoku.domain.Board;
import com.sudoku.domain.Grid;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardFormatterTest {

    private static final Grid GRID = Grid.of(List.of(
            List.of(5, 3, 0, 0, 7, 0, 0, 0, 0),
            List.of(6, 0, 0, 1, 9, 5, 0, 0, 0),
            List.of(0, 9, 8, 0, 0, 0, 0, 6, 0),
            List.of(8, 0, 0, 0, 6, 0, 0, 0, 3),
            List.of(4, 0, 0, 8, 0, 3, 0, 0, 1),
            List.of(7, 0, 0, 0, 2, 0, 0, 0, 6),
            List.of(0, 6, 0, 0, 0, 0, 2, 8, 0),
            List.of(0, 0, 0, 4, 1, 9, 0, 0, 5),
            List.of(0, 0, 0, 0, 8, 0, 0, 7, 9)
    ));

    @Test
    void format_producesNineRows() {
        String result = BoardFormatter.format(Board.fromGrid(GRID));
        long rowCount = result.lines().filter(l -> l.startsWith("Row")).count();
        assertEquals(9, rowCount);
    }

    @Test
    void format_hasBoxSeparatorsAtRowsThreeAndSix() {
        String result = BoardFormatter.format(Board.fromGrid(GRID));
        long separatorCount = result.lines().filter(l -> l.contains("------+-------+------")).count();
        assertEquals(2, separatorCount);
    }

    @Test
    void format_rendersFilledCellAsDigit() {
        String result = BoardFormatter.format(Board.fromGrid(GRID));
        // Cell (0,0) = 5
        assertTrue(result.lines().findFirst().orElseThrow().contains("5"));
    }

    @Test
    void format_rendersEmptyCellAsUnderscore() {
        String result = BoardFormatter.format(Board.fromGrid(GRID));
        // Cell (0,2) = 0 (empty)
        assertTrue(result.contains("_"));
    }

    @Test
    void format_hasColumnSeparators() {
        String result = BoardFormatter.format(Board.fromGrid(GRID));
        result.lines()
              .filter(l -> l.startsWith("Row"))
              .forEach(l -> assertTrue(l.contains("|"), "expected | in: " + l));
    }

    @Test
    void format_rowLabelsAreOneBased() {
        String result = BoardFormatter.format(Board.fromGrid(GRID));
        assertTrue(result.contains("Row 1:"), "missing Row 1:");
        assertTrue(result.contains("Row 9:"), "missing Row 9:");
        assertFalse(result.contains("Row 0:"), "unexpected Row 0:");
    }
}
