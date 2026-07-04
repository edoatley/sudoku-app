package com.sudoku.puzzle;

import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.ChatMessage;
import com.sudoku.dto.CoachRequest;
import com.sudoku.dto.CoachResponse;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import com.sudoku.puzzle.hint.Difficulty;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Stub implementation for Phase 1.
 *
 * <p>Returns a hardcoded coaching response so the frontend can build against a real endpoint
 * shape. Replaced with the deterministic + Bedrock implementation in Phase 2 and 4.
 */
@ApplicationScoped
public class SudokuCoachServiceImpl implements SudokuCoachService {

    private static final int MAX_HISTORY_MESSAGES = 6;

    private static final HintResponse STUB_HINT = new HintResponse(
            "Full House",
            "full-house",
            Difficulty.EASY,
            10,
            "One of the blocks has only one empty cell remaining.",
            "Look at the top-right 3×3 block — there is only one empty cell.",
            "Row 1, Column 9 must be 4 — it is the only digit missing from the top-right block.",
            List.of(new Coordinate(0, 8)),
            List.of(),
            List.of(new ActionableCell(0, 8, 4)),
            List.of()
    );

    @Override
    public CoachResult coach(CoachRequest request) {
        List<ChatMessage> trimmedHistory = trim(request.history());
        return new CoachResult.Response(new CoachResponse(
                "Hello! I can see you're working on a Sudoku puzzle. " +
                "Take a look around the board — sometimes the easiest moves are hiding in plain sight.",
                STUB_HINT,
                false
        ));
    }

    private List<ChatMessage> trim(List<ChatMessage> history) {
        if (history == null || history.size() <= MAX_HISTORY_MESSAGES) {
            return history == null ? List.of() : history;
        }
        return history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size());
    }
}
