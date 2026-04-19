package com.sudoku.puzzle;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.BoardRequest;
import com.sudoku.dto.CandidatesResponse;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import com.sudoku.dto.PuzzleResponse;
import com.sudoku.dto.ValidationResponse;
import com.sudoku.puzzle.hint.BoardUtils;
import com.sudoku.puzzle.hint.HintStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

/**
 * Core implementation of the Sudoku puzzle domain.
 *
 * <p>Coordinates puzzle generation via {@link PuzzleGenerator} and hint discovery via
 * the ordered chain of {@link HintStrategy} implementations discovered through CDI.
 * Strategies are evaluated in ascending difficulty-rank order so players always receive
 * the simplest applicable logical deduction, preserving the pedagogical progression from
 * beginner techniques (Full House, Naked Single) through advanced patterns (X-Wing, Y-Wing).
 */
@ApplicationScoped
public class SudokuServiceImpl implements SudokuService {

    private final PuzzleGenerator generator;
    private final List<HintStrategy> strategies;

    @Inject
    public SudokuServiceImpl(PuzzleGenerator generator, Instance<HintStrategy> strategyInstance) {
        this.generator = generator;
        this.strategies = strategyInstance.stream()
                .sorted(Comparator.comparingInt(HintStrategy::getDifficultyRank))
                .toList();
    }

    // Test-only constructor (package-private)
    SudokuServiceImpl(List<HintStrategy> strategies) {
        this.generator = new PuzzleGenerator();
        this.strategies = List.copyOf(strategies);
    }

    // Test-only constructor with explicit generator (package-private)
    SudokuServiceImpl(PuzzleGenerator generator, List<HintStrategy> strategies) {
        this.generator = generator;
        this.strategies = List.copyOf(strategies);
    }

    @Override
    public PuzzleResponse generatePuzzle(String difficulty) {
        String normalised = difficulty.toLowerCase();
        var result = generator.generate(normalised);
        return new PuzzleResponse(result.puzzle(), result.solution(), normalised);
    }

    @Override
    public ValidationResponse validatePuzzle(BoardRequest request) {
        List<List<Integer>> solution = request.solutionGrid();
        if (solution != null) {
            return validateAgainstSolution(request.currentGrid(), solution);
        }
        return validateByDuplicates(request.currentGrid());
    }

    private ValidationResponse validateAgainstSolution(List<List<Integer>> currentGrid, List<List<Integer>> solution) {
        Set<Coordinate> errorSet = new LinkedHashSet<>();
        boolean hasEmpty = false;

        for (int r = 0; r < UNIT_SIZE; r++) {
            for (int c = 0; c < UNIT_SIZE; c++) {
                int current = currentGrid.get(r).get(c);
                if (current == 0) {
                    hasEmpty = true;
                } else if (current != solution.get(r).get(c)) {
                    errorSet.add(new Coordinate(r, c));
                }
            }
        }

        boolean isSolved = errorSet.isEmpty() && !hasEmpty;
        return new ValidationResponse(errorSet.isEmpty(), isSolved, List.copyOf(errorSet));
    }

    private ValidationResponse validateByDuplicates(List<List<Integer>> currentGrid) {
        Board board = Board.fromGrid(currentGrid);
        Set<Coordinate> errorSet = BoardUtils.findDuplicatesInBoard(board);

        boolean hasEmpty = false;
        outer:
        for (int r = 0; r < UNIT_SIZE; r++) {
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

    @Override
    public Optional<HintResponse> getHint(BoardRequest request) {
        Board board = Board.fromGrid(request.currentGrid());
        board.calculateAllCandidates();
        int minRank = request.minRank() != null ? request.minRank() : 0;
        List<Integer> excluded = request.excludedRanks() != null ? request.excludedRanks() : List.of();
        for (HintStrategy strategy : strategies) {
            if (strategy.getDifficultyRank() < minRank) continue;
            if (excluded.contains(strategy.getDifficultyRank())) continue;
            Optional<HintResponse> hint = strategy.evaluate(board);
            if (hint.isPresent()) {
                HintResponse h = hint.get();
                boolean hasAction = (h.eliminatedCandidates() != null && !h.eliminatedCandidates().isEmpty())
                        || (h.solvedCells() != null && !h.solvedCells().isEmpty());
                if (hasAction) return hint;
            }
        }
        return Optional.empty();
    }

    @Override
    public CandidatesResponse getCandidates(BoardRequest request) {
        Board board = Board.fromGrid(request.currentGrid());
        board.calculateAllCandidates();
        return new CandidatesResponse(board.toCandidatesGrid());
    }

    @Override
    public Optional<List<List<Integer>>> solveGrid(List<List<Integer>> puzzle) {
        return generator.solveGrid(puzzle);
    }

    @Override
    public boolean hasSingleSolution(List<List<Integer>> grid) {
        return generator.countSolutions(grid) == 1;
    }
}
