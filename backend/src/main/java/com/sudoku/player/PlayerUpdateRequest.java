package com.sudoku.player;

/**
 * Request body for PATCH /players/me.
 *
 * <p>All fields are optional — omitting a field leaves the stored value unchanged.
 * At least one non-null field must be present; the service enforces this constraint
 * and returns HTTP 400 otherwise.
 */
public record PlayerUpdateRequest(String displayName, String avatarKey, Boolean aiCoachEnabled) {}
