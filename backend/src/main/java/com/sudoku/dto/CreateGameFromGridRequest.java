package com.sudoku.dto;

import java.util.List;

public record CreateGameFromGridRequest(List<List<Integer>> originalGrid) {}
