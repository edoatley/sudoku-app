package com.sudoku.game.web;

import java.util.List;

// @spec GL-API-005

/**
 * One buffered puzzle-play action reported by the client on a progress save, for
 * observability only (see {@code docs/llds/game-lifecycle.md} Puzzle-Play Event Logging).
 *
 * <p>A single flat shape covers every client event {@code type}
 * ({@code NUMBER}, {@code NUMBER_CLEAR}, {@code HINT_REQUEST}, {@code HINT_RESPONSE}, and the
 * {@code EVENTS_TRUNCATED} marker); fields not relevant to a given type are null. All fields
 * are client-supplied and untrusted — {@code NUMBER_RESULT} is never sent by the client, it is
 * derived server-side.
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
        List<Integer> excludedRanks
) {}
