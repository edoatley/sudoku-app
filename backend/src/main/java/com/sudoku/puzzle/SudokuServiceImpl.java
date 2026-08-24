package com.sudoku.puzzle;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.domain.Grid;
import com.sudoku.puzzle.web.BoardRequest;
import com.sudoku.puzzle.web.CandidatesResponse;
import com.sudoku.puzzle.web.Coordinate;
import com.sudoku.puzzle.web.HintResponse;
import com.sudoku.puzzle.web.PuzzleResponse;
import com.sudoku.puzzle.web.ValidationResponse;
import com.sudoku.puzzle.hint.BoardUtils;
import com.sudoku.puzzle.hint.HintResult;
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

    // Package-private constructor used by SudokuServiceTestFactory in src/test/java.
    // Strategies must already be sorted by rank.
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
        Grid solution = request.solutionGrid();
        if (solution != null) {
            return validateAgainstSolution(request.currentGrid(), solution);
        }
        return validateByDuplicates(request.currentGrid());
    }

    private ValidationResponse validateAgainstSolution(Grid currentGrid, Grid solution) {
        Set<Coordinate> errorSet = new LinkedHashSet<>();
        boolean hasEmpty = false;

        for (int r = 0; r < UNIT_SIZE; r++) {
            for (int c = 0; c < UNIT_SIZE; c++) {
                int current = currentGrid.cell(r, c);
                if (current == 0) {
                    hasEmpty = true;
                } else if (current != solution.cell(r, c)) {
                    errorSet.add(new Coordinate(r, c));
                }
            }
        }

        boolean isSolved = errorSet.isEmpty() && !hasEmpty;
        return new ValidationResponse(errorSet.isEmpty(), isSolved, List.copyOf(errorSet));
    }

    private ValidationResponse validateByDuplicates(Grid currentGrid) {
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

    // @spec HE-BE-003, HE-BE-004, HE-BE-005, HE-BE-006, HE-BE-007
    @Override
    public HintResult getHint(BoardRequest request) {
        Board board = Board.fromGrid(request.currentGrid());
        board.calculateAllCandidates();

        // A solved board has no empty cells and no duplicates — hints are not meaningful.
        ValidationResponse state = validateByDuplicates(request.currentGrid());
        if (state.isSolved()) {
            return new HintResult.PuzzleSolved();
        }

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
                if (hasAction) return new HintResult.Found(h);
            }
        }
        return new HintResult.NoStrategyApplied();
    }

    @Override
    public CandidatesResponse getCandidates(BoardRequest request) {
        Board board = Board.fromGrid(request.currentGrid());
        board.calculateAllCandidates();
        return new CandidatesResponse(board.toCandidatesGrid());
    }

    // @spec DT-SVC-001
    @Override
    public Optional<Grid> solveGrid(Grid puzzle) {
        return generator.solveGrid(puzzle);
    }

    // @spec DT-SVC-002, HE-BE-035
    @Override
    public boolean hasSingleSolution(Grid grid) {
        return generator.countSolutions(grid) == 1;
    }
}
