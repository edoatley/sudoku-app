package com.sudoku.game;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the lifecycle state of a persisted Sudoku game.
 *
 * <p>A game begins as {@link #IN_PROGRESS} when the player starts a new puzzle.
 * It transitions to {@link #SOLVED} when the player completes the board correctly,
 * or to {@link #ABANDONED} when the player starts a new game while one is already
 * in progress — the server enforces this transition automatically so only one game
 * is ever IN_PROGRESS per player at a time.
 * {@link #IMPORTED} covers games created from an external grid (e.g. via the
 * image-recognition pipeline) where no server-generated solution is available.
 */
public enum GameStatus {

    IN_PROGRESS("IN_PROGRESS"),
    SOLVED("SOLVED"),
    ABANDONED("ABANDONED"),
    IMPORTED("imported");

    private final String value;

    GameStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static GameStatus fromValue(String value) {
        for (GameStatus status : values()) {
            if (status.value.equals(value)) return status;
        }
        throw new IllegalArgumentException("Unknown GameStatus: " + value);
    }
}
