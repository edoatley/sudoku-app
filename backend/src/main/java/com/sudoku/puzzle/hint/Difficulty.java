package com.sudoku.puzzle.hint;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The difficulty tiers used to classify Sudoku solving techniques.
 *
 * Each tier carries a human-readable label that is used as the serialised form
 * in API responses, ensuring the client receives {@code "easy"} rather than
 * {@code "EASY"} without requiring any global Jackson configuration.
 */
public enum Difficulty {
    EASY("easy"),
    MEDIUM("medium"),
    HARD("hard");

    private final String label;

    Difficulty(String label) {
        this.label = label;
    }

    /** Lowercase label used in API responses (e.g. {@code "easy"}). */
    @JsonValue
    public String getLabel() {
        return label;
    }
}
