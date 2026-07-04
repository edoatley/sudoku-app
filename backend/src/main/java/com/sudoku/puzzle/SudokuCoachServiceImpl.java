package com.sudoku.puzzle;

import com.sudoku.dto.BoardRequest;
import com.sudoku.dto.ChatMessage;
import com.sudoku.dto.CoachRequest;
import com.sudoku.dto.CoachResponse;
import com.sudoku.puzzle.hint.HintResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

// @spec SC-BE-001, SC-BE-002, SC-BE-003, SC-API-004

/**
 * Orchestrates coaching responses by running the deterministic hint engine first,
 * then using its result as the coaching message.
 *
 * <p>Phase 2 implementation: returns the hint's nudge text as the AI message.
 * Phase 4 will replace the nudge-text fallback with a real Bedrock call.
 */
@ApplicationScoped
public class SudokuCoachServiceImpl implements SudokuCoachService {

    static final int MAX_HISTORY_MESSAGES = 6;

    private static final String NO_MOVES_MESSAGE =
            "I've looked carefully at the board but I can't find a logical next move. " +
            "Check whether any cells have been filled incorrectly — the board may be in " +
            "an invalid state.";

    @Inject
    SudokuService sudokuService;

    @Override
    public CoachResult coach(CoachRequest request) {
        List<ChatMessage> trimmedHistory = trim(request.history());

        // @spec SC-BE-001 — hint engine runs first, before any external AI call
        HintResult hintResult = sudokuService.getHint(new BoardRequest(request.board()));

        return switch (hintResult) {
            // @spec SC-BE-002
            case HintResult.PuzzleSolved ignored -> new CoachResult.PuzzleSolved();

            case HintResult.NoStrategyApplied ignored -> new CoachResult.Response(
                    new CoachResponse(NO_MOVES_MESSAGE, null, false));

            // @spec SC-BE-003 — technique context available; Phase 4 replaces nudge with Bedrock prose
            case HintResult.Found f -> new CoachResult.Response(
                    new CoachResponse(f.hint().nudge(), f.hint(), false));
        };
    }

    // @spec SC-API-004
    static List<ChatMessage> trim(List<ChatMessage> history) {
        if (history == null) {
            return List.of();
        }
        if (history.size() <= MAX_HISTORY_MESSAGES) {
            return history;
        }
        return history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size());
    }
}
