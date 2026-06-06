package com.sudoku.dto;

import java.util.Map;

// @spec LT-BE-018
public record LeaderboardEntry(
        String userId,
        String displayName,
        String avatarKey,
        int rank,
        int totalWins,
        int totalGames,
        int avgScore,
        Integer avgElapsedSeconds,
        Map<String, Integer> bestTimeByDifficulty
) {}
