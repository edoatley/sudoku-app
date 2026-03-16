package com.sudoku.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Cell {

    private final int row;
    private final int col;
    private int value;
    private Set<Integer> candidates;

    public Cell(int row, int col, int value) {
        if (row < 0 || row > 8) throw new IllegalArgumentException("row must be in [0,8], got: " + row);
        if (col < 0 || col > 8) throw new IllegalArgumentException("col must be in [0,8], got: " + col);
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
