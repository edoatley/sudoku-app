package com.sudoku.dto;

// @spec SC-API-010, SC-API-011, SC-API-012

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
 */
public record CoachResponse(String aiMessage, HintResponse hint, boolean revealHint) {}
