package com.sudoku.dto;

import java.util.List;

public record GameState(
        String gameId,
        String difficulty,
        List<List<Integer>> originalGrid,
        List<List<Integer>> currentGrid,
        List<List<List<Integer>>> candidates,
        int timeSpentSeconds,
        String status
) {}
