package com.sudoku.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

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

    // Correct candidates computed by pure row/col/block elimination from EASY_GRID
    private static final List<List<List<Integer>>> EXPECTED_CANDIDATES = List.of(
            // row 0: [5,3,0,0,7,0,0,0,0]
            List.of(List.of(), List.of(), List.of(1,2,4), List.of(2,6), List.of(), List.of(2,4,6,8), List.of(1,4,8,9), List.of(1,2,4,9), List.of(2,4,8)),
            // row 1: [6,0,0,1,9,5,0,0,0]
            List.of(List.of(), List.of(2,4,7), List.of(2,4,7), List.of(), List.of(), List.of(), List.of(3,4,7,8), List.of(2,3,4), List.of(2,4,7,8)),
            // row 2: [0,9,8,0,0,0,0,6,0]
            List.of(List.of(1,2), List.of(), List.of(), List.of(2,3), List.of(3,4), List.of(2,4), List.of(1,3,4,5,7), List.of(), List.of(2,4,7)),
            // row 3: [8,0,0,0,6,0,0,0,3]
            List.of(List.of(), List.of(1,2,5), List.of(1,2,5,9), List.of(5,7,9), List.of(), List.of(1,4,7), List.of(4,5,7,9), List.of(2,4,5,9), List.of()),
            // row 4: [4,0,0,8,0,3,0,0,1]
            List.of(List.of(), List.of(2,5), List.of(2,5,6,9), List.of(), List.of(5), List.of(), List.of(5,7,9), List.of(2,5,9), List.of()),
            // row 5: [7,0,0,0,2,0,0,0,6]
            List.of(List.of(), List.of(1,5), List.of(1,3,5,9), List.of(5,9), List.of(), List.of(1,4), List.of(4,5,8,9), List.of(4,5,9), List.of()),
            // row 6: [0,6,0,0,0,0,2,8,0]
            List.of(List.of(1,3,9), List.of(), List.of(1,3,4,5,7,9), List.of(3,5,7), List.of(3,5), List.of(7), List.of(), List.of(), List.of(4)),
            // row 7: [0,0,0,4,1,9,0,0,5]
            List.of(List.of(2,3), List.of(2,7,8), List.of(2,3,7), List.of(), List.of(), List.of(), List.of(3,6), List.of(3), List.of()),
            // row 8: [0,0,0,0,8,0,0,7,9]
            List.of(List.of(1,2,3), List.of(1,2,4,5), List.of(1,2,3,4,5), List.of(2,3,5,6), List.of(), List.of(2,6), List.of(1,3,4,6), List.of(), List.of())
    );

    @Test
    void fromGridBuildsCellsAtCorrectPositions() {
        Board board = Board.fromGrid(EASY_GRID);
        assertEquals(5, board.getCell(0, 0).value());
        assertEquals(3, board.getCell(0, 1).value());
        assertEquals(0, board.getCell(0, 2).value());
        assertTrue(board.getCell(0, 2).isEmpty());
        assertEquals(9, board.getCell(8, 8).value());
    }

    @Test
    void fromGridThrowsOnNull() {
        assertThrows(IllegalArgumentException.class, () -> Board.fromGrid(null));
    }

    @Test
    void fromGridThrowsOnWrongRowCount() {
        List<List<Integer>> bad = EASY_GRID.subList(0, 8);
        assertThrows(IllegalArgumentException.class, () -> Board.fromGrid(bad));
    }

    @Test
    void fromGridThrowsOnWrongColumnCount() {
        List<List<Integer>> bad = List.of(
                List.of(5, 3, 0, 0, 7, 0, 0, 0), // only 8 cols
                List.of(6, 0, 0, 1, 9, 5, 0, 0, 0),
                List.of(0, 9, 8, 0, 0, 0, 0, 6, 0),
                List.of(8, 0, 0, 0, 6, 0, 0, 0, 3),
                List.of(4, 0, 0, 8, 0, 3, 0, 0, 1),
                List.of(7, 0, 0, 0, 2, 0, 0, 0, 6),
                List.of(0, 6, 0, 0, 0, 0, 2, 8, 0),
                List.of(0, 0, 0, 4, 1, 9, 0, 0, 5),
                List.of(0, 0, 0, 0, 8, 0, 0, 7, 9)
        );
        assertThrows(IllegalArgumentException.class, () -> Board.fromGrid(bad));
    }

    @Test
    void getRowReturnsCorrectValues() {
        Board board = Board.fromGrid(EASY_GRID);
        List<Cell> row0 = board.getRow(0);
        assertEquals(9, row0.size());
        assertEquals(5, row0.get(0).value());
        assertEquals(3, row0.get(1).value());
        assertEquals(0, row0.get(2).value());
        assertEquals(7, row0.get(4).value());
    }

    @Test
    void getColumnReturnsCorrectValues() {
        Board board = Board.fromGrid(EASY_GRID);
        List<Cell> col0 = board.getColumn(0);
        assertEquals(9, col0.size());
        assertEquals(5, col0.get(0).value()); // row 0, col 0
        assertEquals(6, col0.get(1).value()); // row 1, col 0
        assertEquals(0, col0.get(2).value()); // row 2, col 0
        assertEquals(8, col0.get(3).value()); // row 3, col 0
        assertEquals(4, col0.get(4).value()); // row 4, col 0
        assertEquals(7, col0.get(5).value()); // row 5, col 0
    }

    @Test
    void getBlock0IsTopLeft() {
        Board board = Board.fromGrid(EASY_GRID);
        List<Cell> block = board.getBlock(0);
        assertEquals(9, block.size());
        // rows 0-2, cols 0-2: [5,3,0],[6,0,0],[0,9,8]
        assertEquals(5, block.get(0).value());
        assertEquals(3, block.get(1).value());
        assertEquals(0, block.get(2).value());
        assertEquals(6, block.get(3).value());
        assertEquals(9, block.get(7).value());
        assertEquals(8, block.get(8).value());
    }

    @Test
    void getBlock4IsCentre() {
        Board board = Board.fromGrid(EASY_GRID);
        List<Cell> block = board.getBlock(4);
        // rows 3-5, cols 3-5: [0,6,0],[8,0,3],[0,2,0]
        assertEquals(0, block.get(0).value());
        assertEquals(6, block.get(1).value());
        assertEquals(0, block.get(2).value());
        assertEquals(8, block.get(3).value());
        assertEquals(0, block.get(4).value());
        assertEquals(3, block.get(5).value());
    }

    @Test
    void getBlock8IsBottomRight() {
        Board board = Board.fromGrid(EASY_GRID);
        List<Cell> block = board.getBlock(8);
        // rows 6-8, cols 6-8: [2,8,0],[0,0,5],[0,7,9]
        assertEquals(2, block.get(0).value());
        assertEquals(8, block.get(1).value());
        assertEquals(0, block.get(2).value());
        assertEquals(0, block.get(3).value());
        assertEquals(0, block.get(4).value());
        assertEquals(5, block.get(5).value());
        assertEquals(0, block.get(6).value());
        assertEquals(7, block.get(7).value());
        assertEquals(9, block.get(8).value());
    }

    @Test
    void calculateAllCandidatesSpotCheckCell0_2() {
        Board board = Board.fromGrid(EASY_GRID);
        board.calculateAllCandidates();
        // row 0 has {5,3,7}, col 2 has {8}, block 0 has {5,3,6,9,8}
        // used = {3,5,6,7,8,9} → candidates = {1,2,4}
        assertEquals(java.util.Set.of(1, 2, 4), board.getCell(0, 2).candidates());
    }

    @Test
    void calculateAllCandidatesFilledCellHasNoCandidates() {
        Board board = Board.fromGrid(EASY_GRID);
        board.calculateAllCandidates();
        assertTrue(board.getCell(0, 0).candidates().isEmpty()); // value=5, not empty
    }

    @Test
    void toCandidatesGridMatchesMockServiceResponse() {
        Board board = Board.fromGrid(EASY_GRID);
        board.calculateAllCandidates();
        List<List<List<Integer>>> actual = board.toCandidatesGrid();
        assertEquals(EXPECTED_CANDIDATES, actual);
    }
}
