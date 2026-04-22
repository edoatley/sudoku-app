package com.sudoku.puzzle.developer;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.domain.Grid;
import com.sudoku.dto.BoardRequest;
import com.sudoku.dto.CandidatesResponse;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.PuzzleResponse;
import com.sudoku.dto.ValidationResponse;
import com.sudoku.puzzle.hint.BoardUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

public class MockSudokuService {

    private static final Grid EASY_GRID = Grid.of(List.of(
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

    private static final Grid MEDIUM_GRID = Grid.of(List.of(
            List.of(0, 0, 0, 2, 6, 0, 7, 0, 1),
            List.of(6, 8, 0, 0, 7, 0, 0, 9, 0),
            List.of(1, 9, 0, 0, 0, 4, 5, 0, 0),
            List.of(8, 2, 0, 1, 0, 0, 0, 4, 0),
            List.of(0, 0, 4, 6, 0, 2, 9, 0, 0),
            List.of(0, 5, 0, 0, 0, 3, 0, 2, 8),
            List.of(0, 0, 9, 3, 0, 0, 0, 7, 4),
            List.of(0, 4, 0, 0, 5, 0, 0, 3, 6),
            List.of(7, 0, 3, 0, 1, 8, 0, 0, 0)
    ));

    private static final Grid HARD_GRID = Grid.of(List.of(
            List.of(0, 0, 0, 0, 0, 0, 6, 8, 0),
            List.of(0, 0, 0, 0, 7, 3, 0, 0, 9),
            List.of(3, 0, 9, 0, 0, 0, 0, 4, 5),
            List.of(4, 9, 0, 0, 0, 0, 0, 0, 0),
            List.of(8, 0, 3, 0, 5, 0, 9, 0, 2),
            List.of(0, 0, 0, 0, 0, 0, 0, 3, 6),
            List.of(9, 6, 0, 0, 0, 0, 3, 0, 8),
            List.of(7, 0, 0, 6, 8, 0, 0, 0, 0),
            List.of(0, 2, 8, 0, 0, 0, 0, 0, 0)
    ));

    private static final Map<String, PuzzleResponse> PUZZLE_MAP = Map.of(
            "easy",   new PuzzleResponse(EASY_GRID,   null, "easy"),
            "medium", new PuzzleResponse(MEDIUM_GRID, null, "medium"),
            "hard",   new PuzzleResponse(HARD_GRID,   null, "hard"),
            "expert", new PuzzleResponse(HARD_GRID,   null, "expert")
    );

    public PuzzleResponse generatePuzzle(String difficulty) {
        return switch (difficulty.toLowerCase()) {
            case "easy"   -> PUZZLE_MAP.get("easy");
            case "hard"   -> PUZZLE_MAP.get("hard");
            case "expert" -> PUZZLE_MAP.get("expert");
            default       -> PUZZLE_MAP.get("medium");
        };
    }

    public ValidationResponse validatePuzzle(BoardRequest request) {
        Board board = Board.fromGrid(request.currentGrid());
        Set<Coordinate> errorSet = BoardUtils.findDuplicatesInBoard(board);

        boolean hasEmpty = IntStream.range(0, 9)
                .mapToObj(board::getRow)
                .flatMap(List::stream)
                .anyMatch(Cell::isEmpty);

        boolean isSolved = errorSet.isEmpty() && !hasEmpty;
        return new ValidationResponse(errorSet.isEmpty(), isSolved, List.copyOf(errorSet));
    }

    public CandidatesResponse getCandidates(BoardRequest request) {
        Board board = Board.fromGrid(request.currentGrid());
        board.calculateAllCandidates();
        return new CandidatesResponse(board.toCandidatesGrid());
    }
}
