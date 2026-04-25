package com.sudoku.puzzle.hint;

import com.sudoku.dto.HintResponse;

/**
 * Typed result of a hint request, distinguishing three distinct outcomes that callers
 * must handle differently.
 *
 * <p>Using a sealed type instead of {@code Optional<HintResponse>} makes the
 * "puzzle already solved" case explicit at the type level — callers cannot accidentally
 * treat a solved puzzle the same as a board where no strategy applied.
 */
public sealed interface HintResult permits HintResult.Found, HintResult.PuzzleSolved, HintResult.NoStrategyApplied {

    /** A strategy found an actionable hint. */
    record Found(HintResponse hint) implements HintResult {}

    /** The puzzle is already complete — no hint is meaningful. */
    record PuzzleSolved() implements HintResult {}

    /** All eligible strategies were exhausted without producing an actionable result. */
    record NoStrategyApplied() implements HintResult {}
}
