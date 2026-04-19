package com.sudoku.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.sudoku.domain.SudokuConstants.BOX_SIZE;
import static com.sudoku.domain.SudokuConstants.MAX_DIGIT;
import static com.sudoku.domain.SudokuConstants.MIN_DIGIT;
import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

/**
 * Immutable-structure, mutable-state representation of a 9×9 Sudoku board.
 *
 * <p>The grid is built once via {@link #fromGrid(List)} and the 81 {@link Cell} objects
 * it owns are mutated in-place by solving and hint strategies. The class exposes
 * structured views of the grid — individual cells, full rows, columns, and the nine
 * 3×3 blocks — that are used by constraint-propagation and pattern-detection logic.
 *
 * <p>Candidate management is also centralised here: {@link #calculateAllCandidates()}
 * populates every empty cell's pencil marks by eliminating digits already present in
 * the same row, column, and block, and {@link #toCandidatesGrid()} serialises those
 * marks into the nested-list format expected by {@link com.sudoku.dto.CandidatesResponse}.
 */
public final class Board {

    private final Cell[][] cells;

    private Board(Cell[][] cells) {
        this.cells = cells;
    }

    // @spec SL-DATA-001, SL-DATA-002, SL-DATA-003, SL-DATA-004, DT-BOARD-001
    public static Board fromGrid(Grid grid) {
        if (grid == null || grid.rows() == null || grid.size() != UNIT_SIZE) {
            throw new InvalidGridException("Grid must be 9 rows");
        }
        Cell[][] cells = new Cell[UNIT_SIZE][UNIT_SIZE];
        for (int r = 0; r < UNIT_SIZE; r++) {
            List<Integer> row = grid.row(r);
            if (row == null || row.size() != UNIT_SIZE) {
                throw new InvalidGridException("Each row must have 9 columns, row " + r + " is invalid");
            }
            for (int c = 0; c < UNIT_SIZE; c++) {
                Integer val = row.get(c);
                if (val == null || val < 0 || val > MAX_DIGIT) {
                    throw new InvalidGridException("Cell value must be in [0,9], got: " + val + " at (" + r + "," + c + ")");
                }
                cells[r][c] = new Cell(r, c, val);
            }
        }
        return new Board(cells);
    }

    // @spec SL-DATA-001
    public Cell getCell(int row, int col) {
        return cells[row][col];
    }

    // @spec SL-DATA-001, SL-DATA-005
    public List<Cell> getRow(int rowIndex) {
        List<Cell> row = new ArrayList<>(UNIT_SIZE);
        for (int c = 0; c < UNIT_SIZE; c++) {
            row.add(cells[rowIndex][c]);
        }
        return Collections.unmodifiableList(row);
    }

    // @spec SL-DATA-001, SL-DATA-005
    public List<Cell> getColumn(int colIndex) {
        return Collections.unmodifiableList(
            IntStream.range(0, UNIT_SIZE).mapToObj(r -> cells[r][colIndex]).toList()
        );
    }

    // @spec SL-DATA-005
    public List<Cell> getBlock(int blockIndex) {
        int startRow = (blockIndex / BOX_SIZE) * BOX_SIZE;
        int startCol = (blockIndex % BOX_SIZE) * BOX_SIZE;
        List<Cell> block = new ArrayList<>(UNIT_SIZE);
        for (int r = startRow; r < startRow + BOX_SIZE; r++) {
            for (int c = startCol; c < startCol + BOX_SIZE; c++) {
                block.add(cells[r][c]);
            }
        }
        return Collections.unmodifiableList(block);
    }

    // @spec SL-PROC-001, SL-PROC-002
    public void calculateAllCandidates() {
        for (int r = 0; r < UNIT_SIZE; r++) {
            for (int c = 0; c < UNIT_SIZE; c++) {
                Cell cell = cells[r][c];
                if (cell.isEmpty()) {
                    Set<Integer> used = collectUsedDigits(r, c);
                    Set<Integer> candidates = new HashSet<>();
                    for (int d = MIN_DIGIT; d <= MAX_DIGIT; d++) {
                        if (!used.contains(d)) candidates.add(d);
                    }
                    cell.setCandidates(candidates);
                }
            }
        }
    }

    // @spec SL-PROC-003, DT-BOARD-002
    public CandidatesGrid toCandidatesGrid() {
        List<List<List<Integer>>> grid = new ArrayList<>(UNIT_SIZE);
        for (int r = 0; r < UNIT_SIZE; r++) {
            List<List<Integer>> row = new ArrayList<>(UNIT_SIZE);
            for (int c = 0; c < UNIT_SIZE; c++) {
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
        return CandidatesGrid.of(Collections.unmodifiableList(grid));
    }

    private Set<Integer> collectUsedDigits(int row, int col) {
        Set<Integer> used = new HashSet<>();
        for (Cell c : getRow(row)) {
            if (!c.isEmpty()) used.add(c.value());
        }
        for (Cell c : getColumn(col)) {
            if (!c.isEmpty()) used.add(c.value());
        }
        int blockIndex = (row / BOX_SIZE) * BOX_SIZE + (col / BOX_SIZE);
        for (Cell c : getBlock(blockIndex)) {
            if (!c.isEmpty()) used.add(c.value());
        }
        return used;
    }
}
