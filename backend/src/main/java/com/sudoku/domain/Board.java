package com.sudoku.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public final class Board {

    private final Cell[][] cells;

    private Board(Cell[][] cells) {
        this.cells = cells;
    }

    public static Board fromGrid(List<List<Integer>> grid) {
        if (grid == null || grid.size() != 9) {
            throw new IllegalArgumentException("Grid must be 9 rows");
        }
        Cell[][] cells = new Cell[9][9];
        for (int r = 0; r < 9; r++) {
            List<Integer> row = grid.get(r);
            if (row == null || row.size() != 9) {
                throw new IllegalArgumentException("Each row must have 9 columns, row " + r + " is invalid");
            }
            for (int c = 0; c < 9; c++) {
                Integer val = row.get(c);
                if (val == null || val < 0 || val > 9) {
                    throw new IllegalArgumentException("Cell value must be in [0,9], got: " + val + " at (" + r + "," + c + ")");
                }
                cells[r][c] = new Cell(r, c, val);
            }
        }
        return new Board(cells);
    }

    public Cell getCell(int row, int col) {
        return cells[row][col];
    }

    public List<Cell> getRow(int rowIndex) {
        List<Cell> row = new ArrayList<>(9);
        for (int c = 0; c < 9; c++) {
            row.add(cells[rowIndex][c]);
        }
        return Collections.unmodifiableList(row);
    }

    public List<Cell> getColumn(int colIndex) {
        return Collections.unmodifiableList(
            IntStream.range(0, 9).mapToObj(r -> cells[r][colIndex]).toList()
        );
    }

    public List<Cell> getBlock(int blockIndex) {
        int startRow = (blockIndex / 3) * 3;
        int startCol = (blockIndex % 3) * 3;
        List<Cell> block = new ArrayList<>(9);
        for (int r = startRow; r < startRow + 3; r++) {
            for (int c = startCol; c < startCol + 3; c++) {
                block.add(cells[r][c]);
            }
        }
        return Collections.unmodifiableList(block);
    }

    public void calculateAllCandidates() {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                Cell cell = cells[r][c];
                if (cell.isEmpty()) {
                    Set<Integer> used = collectUsedDigits(r, c);
                    Set<Integer> candidates = new HashSet<>();
                    for (int d = 1; d <= 9; d++) {
                        if (!used.contains(d)) candidates.add(d);
                    }
                    cell.setCandidates(candidates);
                }
            }
        }
    }

    public List<List<List<Integer>>> toCandidatesGrid() {
        List<List<List<Integer>>> grid = new ArrayList<>(9);
        for (int r = 0; r < 9; r++) {
            List<List<Integer>> row = new ArrayList<>(9);
            for (int c = 0; c < 9; c++) {
                Cell cell = cells[r][c];
                if (cell.isEmpty()) {
                    List<Integer> sorted = new ArrayList<>(cell.candidates());
                    Collections.sort(sorted);
                    row.add(Collections.unmodifiableList(sorted));
                } else {
                    row.add(List.of());
                }
            }
            grid.add(Collections.unmodifiableList(row));
        }
        return Collections.unmodifiableList(grid);
    }

    private Set<Integer> collectUsedDigits(int row, int col) {
        Set<Integer> used = new HashSet<>();
        for (Cell c : getRow(row)) {
            if (!c.isEmpty()) used.add(c.value());
        }
        for (Cell c : getColumn(col)) {
            if (!c.isEmpty()) used.add(c.value());
        }
        int blockIndex = (row / 3) * 3 + (col / 3);
        for (Cell c : getBlock(blockIndex)) {
            if (!c.isEmpty()) used.add(c.value());
        }
        return used;
    }
}
