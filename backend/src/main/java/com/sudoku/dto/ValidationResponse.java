package com.sudoku.dto;

import java.util.List;

public record ValidationResponse(boolean isValid, boolean isSolved, List<Coordinate> errors) {}
