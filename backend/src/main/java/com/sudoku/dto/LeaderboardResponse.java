package com.sudoku.dto;

import java.util.List;

// @spec LT-API-001
public record LeaderboardResponse(List<LeaderboardEntry> entries) {}
