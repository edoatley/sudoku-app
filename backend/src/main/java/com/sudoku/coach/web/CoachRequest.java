package com.sudoku.coach.web;

import com.sudoku.domain.Grid;

import java.util.List;

// @spec SC-API-002, SC-API-003, SC-API-004, SC-BE-020

/**
 * Request body for the AI coaching endpoint.
 *
 * <p>{@code board} is the player's current 9×9 grid (zeros for empty cells).
 * {@code history} is the recent conversation, most recent last; the backend trims to the
 * last 6 messages if more are sent.
 * {@code userMessage} is what the player just typed or selected via a quick reply chip.
 * {@code gameId} is the game session this coaching turn belongs to; it is logged as {@code pid}
 * so coach turns can be joined with puzzle-play events for the same game. It is nullable — the
 * coach may be invoked outside a saved game (e.g. a demo), in which case {@code pid} is null.
 */
public record CoachRequest(Grid board, List<ChatMessage> history, String userMessage, String gameId) {}
