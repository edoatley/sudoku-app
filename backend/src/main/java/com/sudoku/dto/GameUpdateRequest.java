package com.sudoku.dto;

import java.util.List;

public record GameUpdateRequest(
        List<List<Integer>> currentGrid,
        List<List<List<Integer>>> candidates,
        int timeSpentSeconds,
        Boolean isComplete
) {}
