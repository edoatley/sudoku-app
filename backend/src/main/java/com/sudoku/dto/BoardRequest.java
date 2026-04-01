package com.sudoku.dto;

import java.util.List;

public record BoardRequest(List<List<Integer>> currentGrid, Integer minRank) {

    public BoardRequest(List<List<Integer>> currentGrid) {
        this(currentGrid, null);
    }
}
