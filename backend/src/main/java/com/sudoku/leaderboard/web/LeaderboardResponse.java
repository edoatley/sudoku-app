package com.sudoku.leaderboard.web;

import java.util.List;

// @spec LT-API-001
public record LeaderboardResponse(List<LeaderboardEntry> entries) {}
