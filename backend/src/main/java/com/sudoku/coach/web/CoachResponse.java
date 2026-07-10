package com.sudoku.coach.web;

import com.sudoku.puzzle.web.HintResponse;
// @spec SC-API-010, SC-API-011, SC-API-012, SC-RL-010

/**
 * Response from the AI coaching endpoint.
 *
 * <p>{@code aiMessage} is the coaching prose from the LLM, or the deterministic hint nudge
 * text when the Bedrock call fails or times out.
 * {@code hint} is the full deterministic hint for the current board position, always fully
 * populated — the frontend controls which fields to display based on {@code revealHint}.
 * {@code revealHint} is {@code true} only when the AI message explicitly states the cell
 * and digit, signalling the frontend to show the complete hint (reveal, solvedCells,
 * eliminatedCandidates).
 * {@code tokensUsedThisMonth} is the player's cumulative monthly token count *after* this
 * call — set by {@code CoachResource}, which owns the quota bookkeeping; the domain layer
 * (which constructs the other three fields) always passes 0 here and is overwritten before
 * the response is returned to the client.
 */
public record CoachResponse(String aiMessage, HintResponse hint, boolean revealHint, long tokensUsedThisMonth) {}
