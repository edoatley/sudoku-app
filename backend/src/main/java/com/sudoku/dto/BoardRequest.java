package com.sudoku.dto;

import java.util.List;

public record BoardRequest(List<List<Integer>> currentGrid, Integer minRank, List<Integer> excludedRanks) {

    public BoardRequest(List<List<Integer>> currentGrid) {
        this(currentGrid, null, null);
    }

    public BoardRequest(List<List<Integer>> currentGrid, Integer minRank) {
        this(currentGrid, minRank, null);
    }
}
