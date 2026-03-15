package com.sudoku.dto;

import java.util.List;

public record PuzzleResponse(List<List<Integer>> originalGrid, String difficulty) {}
