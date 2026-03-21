package com.sudoku.player;

public record PlayerProfile(
        String userId,
        String email,
        String displayName,
        String createdAt,
        String updatedAt
) {}
