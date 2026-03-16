package com.sudoku.puzzle;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.BoardRequest;
import com.sudoku.dto.CandidatesResponse;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import com.sudoku.dto.PuzzleResponse;
import com.sudoku.dto.ValidationResponse;
import com.sudoku.puzzle.hint.HintStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SudokuServiceImpl implements SudokuService {

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

    private final List<HintStrategy> strategies;

    @Inject
    public SudokuServiceImpl(Instance<HintStrategy> strategyInstance) {
        this.strategies = strategyInstance.stream()
                .sorted(Comparator.comparingInt(HintStrategy::getDifficultyRank))
                .collect(Collectors.toList());
    }

    // Test-only constructor (package-private)
    SudokuServiceImpl(List<HintStrategy> strategies) {
        this.strategies = List.copyOf(strategies);
    }

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
        Board board = Board.fromGrid(request.currentGrid());
        Set<Coordinate> errorSet = new LinkedHashSet<>();

        for (int i = 0; i < 9; i++) {
            findDuplicates(board.getRow(i), errorSet);
        }
        for (int i = 0; i < 9; i++) {
            findDuplicates(board.getColumn(i), errorSet);
        }
        for (int i = 0; i < 9; i++) {
            findDuplicates(board.getBlock(i), errorSet);
        }

        boolean hasEmpty = false;
        outer:
        for (int r = 0; r < 9; r++) {
            for (Cell cell : board.getRow(r)) {
                if (cell.isEmpty()) {
                    hasEmpty = true;
                    break outer;
                }
            }
        }

        boolean isSolved = errorSet.isEmpty() && !hasEmpty;
        return new ValidationResponse(errorSet.isEmpty(), isSolved, List.copyOf(errorSet));
    }

    private void findDuplicates(List<Cell> unit, Set<Coordinate> errors) {
        Set<Integer> seen = new HashSet<>();
        Set<Integer> duplicates = new HashSet<>();
        for (Cell cell : unit) {
            if (!cell.isEmpty()) {
                if (!seen.add(cell.value())) duplicates.add(cell.value());
            }
        }
        for (Cell cell : unit) {
            if (!cell.isEmpty() && duplicates.contains(cell.value())) {
                errors.add(new Coordinate(cell.row(), cell.col()));
            }
        }
    }

    @Override
    public Optional<HintResponse> getHint(BoardRequest request) {
        Board board = Board.fromGrid(request.currentGrid());
        board.calculateAllCandidates();
        for (HintStrategy strategy : strategies) {
            Optional<HintResponse> hint = strategy.evaluate(board);
            if (hint.isPresent()) return hint;
        }
        return Optional.empty();
    }

    @Override
    public CandidatesResponse getCandidates(BoardRequest request) {
        Board board = Board.fromGrid(request.currentGrid());
        board.calculateAllCandidates();
        return new CandidatesResponse(board.toCandidatesGrid());
    }
}
