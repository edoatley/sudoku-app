package com.sudoku.dto;

import java.util.List;

public record CandidatesResponse(List<List<List<Integer>>> candidatesGrid) {}
