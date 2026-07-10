package com.sudoku.coach;

import com.sudoku.coach.bedrock.BedrockCoachClient;
import com.sudoku.domain.Board;
import com.sudoku.puzzle.SudokuService;
import com.sudoku.puzzle.web.BoardRequest;
import com.sudoku.coach.web.ChatMessage;
import com.sudoku.coach.web.CoachRequest;
import com.sudoku.coach.web.CoachResponse;
import com.sudoku.puzzle.hint.HintResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

// @spec SC-BE-001, SC-BE-002, SC-BE-003, SC-BE-007, SC-BE-009, SC-API-004

/**
 * Orchestrates coaching responses by running the deterministic hint engine first,
 * then calling Bedrock to generate conversational coaching prose.
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

    @Inject
    BedrockCoachClient bedrockCoachClient;

    @Override
    public CoachResult coach(CoachRequest request) {
        List<ChatMessage> trimmedHistory = trim(request.history());

        // @spec SC-BE-001 — hint engine runs first, before any external AI call
        HintResult hintResult = sudokuService.getHint(new BoardRequest(request.board()));

        return switch (hintResult) {
            // @spec SC-BE-002
            case HintResult.PuzzleSolved ignored -> new CoachResult.PuzzleSolved();

            case HintResult.NoStrategyApplied ignored -> new CoachResult.Response(
                    new CoachResponse(NO_MOVES_MESSAGE, null, false), 0L);

            // @spec SC-BE-003, SC-BE-009 — one Bedrock call; falls back to nudge on error
            case HintResult.Found f -> {
                // @spec SC-BE-007 — candidates computed once here (only on this path — PuzzleSolved
                // and NoStrategyApplied never reach BedrockCoachClient, so computing them there
                // would be wasted work), then passed through rather than recomputed downstream.
                Board board = Board.fromGrid(request.board());
                board.calculateAllCandidates();
                BedrockCoachClient.CallResult result = bedrockCoachClient.call(
                        request.gameId(), request.userMessage(), f.hint(), trimmedHistory, board);
                yield new CoachResult.Response(
                        new CoachResponse(result.reply().aiMessage(), f.hint(), result.reply().revealHint()),
                        result.tokensUsed());
            }
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
