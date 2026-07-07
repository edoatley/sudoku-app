package com.sudoku.puzzle.web;

import com.sudoku.puzzle.hint.Difficulty;

import java.util.List;

/**
 * The result of applying a single hint strategy to the current board state.
 *
 * <p>Contains everything the frontend needs to render a layered hint UX:
 * <ul>
 *   <li>{@code techniqueName} / {@code markdownSlug} — human-readable name and a slug
 *       used to load the matching tutorial markdown from the client bundle.</li>
 *   <li>{@code difficulty} / {@code strategyRank} — classification of the technique so
 *       the UI can gate progression and style difficulty badges.</li>
 *   <li>{@code nudge} / {@code focus} / {@code reveal} — three escalating levels of
 *       textual explanation shown in sequence as the player requests more help.</li>
 *   <li>{@code highlightCells} — cells the player should inspect.</li>
 *   <li>{@code focusCandidates} — pencil marks central to the logic of the technique.</li>
 *   <li>{@code eliminatedCandidates} — pencil marks that can be removed as a result.</li>
 *   <li>{@code solvedCells} — cells that can have a definite value placed immediately.</li>
 * </ul>
 */
public record HintResponse(
        String techniqueName,
        String markdownSlug,
        Difficulty difficulty,
        int strategyRank,
        String nudge,
        String focus,
        String reveal,
        List<Coordinate> highlightCells,
        List<CoordinateCandidate> eliminatedCandidates,
        List<ActionableCell> solvedCells,
        List<CoordinateCandidate> focusCandidates) {}
