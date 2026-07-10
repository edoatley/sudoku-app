package com.sudoku.game.web;

import java.util.List;

// @spec GL-API-005

/**
 * One buffered puzzle-play action reported by the client on a progress save, for
 * observability only (see {@code docs/llds/game-lifecycle.md} Puzzle-Play Event Logging).
 *
 * <p>A single flat shape covers every client event {@code type}
 * ({@code NUMBER}, {@code NUMBER_CLEAR}, {@code HINT_REQUEST}, {@code HINT_RESPONSE},
 * {@code UNDO}, and the {@code EVENTS_TRUNCATED} marker); fields not relevant to a given type
 * are null. All fields are client-supplied and untrusted — {@code NUMBER_RESULT} is never sent
 * by the client, it is derived server-side.
 *
 * <p>For {@code UNDO}, {@code v} is the digit removed from the cell and {@code prevV} is the
 * digit (or 0 for empty) restored in its place; {@code undoneType} names the original action
 * being reversed (currently always {@code NUMBER} — candidate-mode actions are never buffered).
 */
public record PuzzleEvent(
        String type,
        Integer r,
        Integer c,
        Integer v,
        String cid,
        Long clientTs,
        String techniqueName,
        Integer strategyRank,
        String difficulty,
        Boolean found,
        Integer minRank,
        List<Integer> excludedRanks,
        Integer prevV,
        String undoneType
) {}
