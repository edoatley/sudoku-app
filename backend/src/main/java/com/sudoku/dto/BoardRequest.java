package com.sudoku.dto;

import java.util.List;

public record BoardRequest(List<List<Integer>> currentGrid) {}
