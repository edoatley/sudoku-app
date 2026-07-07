package com.sudoku.coach;

import com.sudoku.coach.web.CoachRequest;
import com.sudoku.coach.web.CoachResponse;
import com.sudoku.puzzle.hint.HintResult;

/**
 * Coaching service that combines the deterministic hint engine with an AI tutoring layer.
 *
 * <p>Implementors must run the hint engine before calling any external AI service so that
 * board analysis is always grounded in mathematically correct results.
 * Returns a {@link HintResult.PuzzleSolved} signal when the board is complete and no
 * coaching is meaningful.
 */
public interface SudokuCoachService {

    /**
     * Produces a coaching response for the player's current message.
     *
     * @return a {@link CoachResult} — either a {@link CoachResult.Response} containing
     *         the AI message and deterministic hint, or a {@link CoachResult.PuzzleSolved}
     *         when the board is already complete.
     */
    CoachResult coach(CoachRequest request);

    sealed interface CoachResult permits CoachResult.Response, CoachResult.PuzzleSolved {
        record Response(CoachResponse coachResponse, long tokensUsed) implements CoachResult {}
        record PuzzleSolved() implements CoachResult {}
    }
}
