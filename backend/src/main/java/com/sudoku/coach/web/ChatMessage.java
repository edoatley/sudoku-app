package com.sudoku.coach.web;

/**
 * A single turn in the coaching conversation.
 *
 * <p>{@code role} is either "user" (the player) or "assistant" (the AI coach).
 * The frontend maintains the history array and sends the last N turns with each request.
 */
public record ChatMessage(String role, String content) {}
