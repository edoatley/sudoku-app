package com.sudoku.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

/**
 * Mutable model of a single cell in a 9×9 Sudoku board.
 *
 * <p>A cell holds its fixed position ({@code row}, {@code col}), its current digit
 * ({@code 0} meaning empty), and the set of pencil-mark candidates still valid for
 * that position. Candidates are cleared automatically when a value is placed via
 * {@link #setValue}, keeping the two pieces of state consistent.
 *
 * <p>This is an internal domain object used by {@link Board} and the hint/solve
 * strategies; it is never serialised directly to JSON.
 */
public final class Cell {

    private final int row;
    private final int col;
    private int value;
    private Set<Integer> candidates;

    public Cell(int row, int col, int value) {
        if (row < 0 || row > UNIT_SIZE - 1) throw new IllegalArgumentException("row must be in [0,8], got: " + row);
        if (col < 0 || col > UNIT_SIZE - 1) throw new IllegalArgumentException("col must be in [0,8], got: " + col);
        if (value < 0 || value > 9) throw new IllegalArgumentException("value must be in [0,9], got: " + value);
        this.row = row;
        this.col = col;
        this.value = value;
        this.candidates = new HashSet<>();
    }

    public int row() { return row; }
    public int col() { return col; }
    public int value() { return value; }

    public boolean isEmpty() { return value == 0; }

    public Set<Integer> candidates() { return Collections.unmodifiableSet(candidates); }

    public void setCandidates(Set<Integer> candidates) {
        this.candidates = new HashSet<>(candidates);
    }

    public void removeCandidate(int digit) {
        candidates.remove(digit);
    }

    public void setValue(int value) {
        if (value < 0 || value > 9) throw new IllegalArgumentException("value must be in [0,9], got: " + value);
        this.value = value;
        this.candidates = new HashSet<>();
    }
}
