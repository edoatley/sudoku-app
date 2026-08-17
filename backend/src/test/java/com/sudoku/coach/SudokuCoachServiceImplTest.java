package com.sudoku.coach;

import com.sudoku.coach.web.CoachRequest;
import com.sudoku.domain.Board;
import com.sudoku.domain.Grid;
import com.sudoku.puzzle.SudokuService;
import com.sudoku.puzzle.hint.Difficulty;
import com.sudoku.puzzle.hint.HintResult;
import com.sudoku.puzzle.web.HintResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// @spec SC-BE-007, SC-BE-009
@QuarkusTest
class SudokuCoachServiceImplTest {

    @InjectMock
    SudokuService sudokuService;

    @InjectMock
    CoachAiClient coachAiClient;

    @Inject
    SudokuCoachService coachService;

    private static final Grid PARTIAL_GRID = Grid.of(List.of(
            List.of(5, 3, 0, 0, 7, 0, 0, 0, 0),
            List.of(6, 0, 0, 1, 9, 5, 0, 0, 0),
            List.of(0, 9, 8, 0, 0, 0, 0, 6, 0),
            List.of(8, 0, 0, 0, 6, 0, 0, 0, 3),
            List.of(4, 0, 0, 8, 0, 3, 0, 0, 1),
            List.of(7, 0, 0, 0, 2, 0, 0, 0, 6),
            List.of(0, 6, 0, 0, 0, 0, 2, 8, 0),
            List.of(0, 0, 0, 4, 1, 9, 0, 0, 5),
            List.of(0, 0, 0, 0, 8, 0, 0, 7, 9)
    ));

    private static final HintResponse HINT = new HintResponse(
            "Naked Single", "naked-single", Difficulty.EASY, 1,
            "Only one digit can go in Row 1, Column 3.", "Look at Row 1.",
            "Place 4 in Row 1, Column 3.", List.of(), List.of(), List.of(), List.of());

    @Test
    void coach_hintFound_delegatesToCoachAiClientExactlyOnce() {
        // @spec SC-BE-009
        when(sudokuService.getHint(any())).thenReturn(new HintResult.Found(HINT));
        when(coachAiClient.call(anyString(), anyString(), any(), anyList(), any(Board.class)))
                .thenReturn(new CoachAiClient.CallResult(
                        new CoachAiClient.AiReply("Let's look at Row 1.", false, "nudge"), 100L));

        coachService.coach(new CoachRequest(PARTIAL_GRID, List.of(), "I'm stuck", "game-1"));

        verify(coachAiClient, times(1)).call(anyString(), anyString(), any(), anyList(), any(Board.class));
    }

    @Test
    void coach_puzzleSolved_neverCallsCoachAiClient() {
        // @spec SC-BE-009, SC-BE-002
        when(sudokuService.getHint(any())).thenReturn(new HintResult.PuzzleSolved());

        coachService.coach(new CoachRequest(PARTIAL_GRID, List.of(), "Am I done?", "game-1"));

        verify(coachAiClient, never()).call(anyString(), anyString(), any(), anyList(), any());
    }

    @Test
    void coach_noStrategyApplied_neverCallsCoachAiClient() {
        // @spec SC-BE-009
        when(sudokuService.getHint(any())).thenReturn(new HintResult.NoStrategyApplied());

        coachService.coach(new CoachRequest(PARTIAL_GRID, List.of(), "Help", "game-1"));

        verify(coachAiClient, never()).call(anyString(), anyString(), any(), anyList(), any());
    }

    @Test
    void coach_hintFound_passesBoardWithCandidatesAlreadyComputed() {
        // @spec SC-BE-007 — candidates computed once by the caller, before CoachAiClient is invoked
        when(sudokuService.getHint(any())).thenReturn(new HintResult.Found(HINT));
        when(coachAiClient.call(anyString(), anyString(), any(), anyList(), any(Board.class)))
                .thenReturn(new CoachAiClient.CallResult(
                        new CoachAiClient.AiReply("ok", false, "nudge"), 100L));

        coachService.coach(new CoachRequest(PARTIAL_GRID, List.of(), "I'm stuck", "game-1"));

        ArgumentCaptor<Board> boardCaptor = ArgumentCaptor.forClass(Board.class);
        verify(coachAiClient).call(anyString(), anyString(), any(), anyList(), boardCaptor.capture());
        Board passedBoard = boardCaptor.getValue();
        // Row 1 (index 0), Col 3 (index 2) is empty (0) in PARTIAL_GRID — must have candidates populated
        assertFalse(passedBoard.getCell(0, 2).candidates().isEmpty());
    }
}
