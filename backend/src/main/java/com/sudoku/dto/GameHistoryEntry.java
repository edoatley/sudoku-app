package com.sudoku.dto;

// @spec GH-DTO-001, GH-DTO-002, GH-DTO-003, LT-BE-005
public record GameHistoryEntry(
        String gameId,
        String difficulty,
        String outcome,
        String endedAt,
        int elapsedSeconds,
        int hintsUsed,
        int score
) {}
