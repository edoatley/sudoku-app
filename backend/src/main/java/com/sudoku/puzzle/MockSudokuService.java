package com.sudoku.puzzle;

import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.BoardRequest;
import com.sudoku.dto.CandidatesResponse;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import com.sudoku.dto.PuzzleResponse;
import com.sudoku.dto.ValidationResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class MockSudokuService implements SudokuService {

    private static final List<List<Integer>> EASY_GRID = List.of(
            List.of(5, 3, 0, 0, 7, 0, 0, 0, 0),
            List.of(6, 0, 0, 1, 9, 5, 0, 0, 0),
            List.of(0, 9, 8, 0, 0, 0, 0, 6, 0),
            List.of(8, 0, 0, 0, 6, 0, 0, 0, 3),
            List.of(4, 0, 0, 8, 0, 3, 0, 0, 1),
            List.of(7, 0, 0, 0, 2, 0, 0, 0, 6),
            List.of(0, 6, 0, 0, 0, 0, 2, 8, 0),
            List.of(0, 0, 0, 4, 1, 9, 0, 0, 5),
            List.of(0, 0, 0, 0, 8, 0, 0, 7, 9)
    );

    private static final List<List<Integer>> MEDIUM_GRID = List.of(
            List.of(0, 0, 0, 2, 6, 0, 7, 0, 1),
            List.of(6, 8, 0, 0, 7, 0, 0, 9, 0),
            List.of(1, 9, 0, 0, 0, 4, 5, 0, 0),
            List.of(8, 2, 0, 1, 0, 0, 0, 4, 0),
            List.of(0, 0, 4, 6, 0, 2, 9, 0, 0),
            List.of(0, 5, 0, 0, 0, 3, 0, 2, 8),
            List.of(0, 0, 9, 3, 0, 0, 0, 7, 4),
            List.of(0, 4, 0, 0, 5, 0, 0, 3, 6),
            List.of(7, 0, 3, 0, 1, 8, 0, 0, 0)
    );

    private static final List<List<Integer>> HARD_GRID = List.of(
            List.of(0, 0, 0, 0, 0, 0, 6, 8, 0),
            List.of(0, 0, 0, 0, 7, 3, 0, 0, 9),
            List.of(3, 0, 9, 0, 0, 0, 0, 4, 5),
            List.of(4, 9, 0, 0, 0, 0, 0, 0, 0),
            List.of(8, 0, 3, 0, 5, 0, 9, 0, 2),
            List.of(0, 0, 0, 0, 0, 0, 0, 3, 6),
            List.of(9, 6, 0, 0, 0, 0, 3, 0, 8),
            List.of(7, 0, 0, 6, 8, 0, 0, 0, 0),
            List.of(0, 2, 8, 0, 0, 0, 0, 0, 0)
    );

    private static final Map<String, PuzzleResponse> PUZZLE_MAP = Map.of(
            "easy",   new PuzzleResponse(EASY_GRID,   "easy"),
            "medium", new PuzzleResponse(MEDIUM_GRID, "medium"),
            "hard",   new PuzzleResponse(HARD_GRID,   "hard"),
            "expert", new PuzzleResponse(HARD_GRID,   "expert")
    );

    private static final ValidationResponse VALID_RESPONSE =
            new ValidationResponse(true, false, List.of());

    private static final HintResponse HINT_RESPONSE = new HintResponse(
            "Naked Single",
            "naked-single",
            "easy",
            "There is a Naked Single hiding somewhere on the board.",
            "Look closely at cell (0, 2) — its row, column, and box together eliminate 8 numbers.",
            "Cell (0, 2) can only be 4. All other numbers appear in its row, column, or 3×3 block.",
            List.of(new Coordinate(0, 2)),
            List.of(),
            List.of(new ActionableCell(0, 2, 4))
    );

    private static final CandidatesResponse CANDIDATES_RESPONSE = new CandidatesResponse(List.of(
            // row 0: [5,3,0,0,7,0,0,0,0]
            List.of(List.of(), List.of(), List.of(1,2,4,6), List.of(2,4,6,8), List.of(), List.of(2,4,6,8), List.of(1,4,8,9), List.of(1,2,4,9), List.of(2,4,8)),
            // row 1: [6,0,0,1,9,5,0,0,0]
            List.of(List.of(), List.of(2,4,7), List.of(2,4,7), List.of(), List.of(), List.of(), List.of(3,4,7,8), List.of(2,3,4), List.of(2,3,4,7,8)),
            // row 2: [0,9,8,0,0,0,0,6,0]
            List.of(List.of(1,2,4), List.of(), List.of(), List.of(2,3,4,6), List.of(3,4,6), List.of(2,4,6), List.of(1,4,7), List.of(), List.of(2,4,7)),
            // row 3: [8,0,0,0,6,0,0,0,3]
            List.of(List.of(), List.of(1,2,4,5,7), List.of(1,2,5,7), List.of(5,7,9), List.of(), List.of(1,4,7,9), List.of(4,5,7,9), List.of(1,2,4,5,9), List.of()),
            // row 4: [4,0,0,8,0,3,0,0,1]
            List.of(List.of(), List.of(2,5,6,7), List.of(2,5,6,7), List.of(), List.of(5,6,9), List.of(), List.of(5,6,7,9), List.of(2,5,9), List.of()),
            // row 5: [7,0,0,0,2,0,0,0,6]
            List.of(List.of(), List.of(1,3,5), List.of(1,3,5), List.of(5,9), List.of(), List.of(1,4,9), List.of(4,5,8,9), List.of(1,3,4,5,9), List.of()),
            // row 6: [0,6,0,0,0,0,2,8,0]
            List.of(List.of(1,3,5,9), List.of(), List.of(1,3,5,7), List.of(5,7,9), List.of(3,4,5,7), List.of(1,4,7), List.of(), List.of(), List.of(4,7)),
            // row 7: [0,0,0,4,1,9,0,0,5]
            List.of(List.of(2,3), List.of(2,3,8), List.of(2,3,7), List.of(), List.of(), List.of(), List.of(3,6,7,8), List.of(2,3), List.of()),
            // row 8: [0,0,0,0,8,0,0,7,9]
            List.of(List.of(1,2,3), List.of(1,3,4), List.of(1,3,4,6), List.of(3,5,6), List.of(), List.of(1,4,6), List.of(1,3,4,6), List.of(), List.of())
    ));

    @Override
    public PuzzleResponse generatePuzzle(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "easy"   -> PUZZLE_MAP.get("easy");
            case "hard"   -> PUZZLE_MAP.get("hard");
            case "expert" -> PUZZLE_MAP.get("expert");
            default       -> PUZZLE_MAP.get("medium");
        };
    }

    @Override
    public ValidationResponse validatePuzzle(BoardRequest request) {
        return VALID_RESPONSE;
    }

    @Override
    public Optional<HintResponse> getHint(BoardRequest request) {
        return Optional.of(HINT_RESPONSE);
    }

    @Override
    public CandidatesResponse getCandidates(BoardRequest request) {
        return CANDIDATES_RESPONSE;
    }
}
