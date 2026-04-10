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
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class SudokuServiceImpl implements SudokuService {

    private final PuzzleGenerator generator;
    private final List<HintStrategy> strategies;

    @Inject
    public SudokuServiceImpl(Instance<HintStrategy> strategyInstance) {
        this.generator = new PuzzleGenerator(new Random());
        this.strategies = strategyInstance.stream()
                .sorted(Comparator.comparingInt(HintStrategy::getDifficultyRank))
                .collect(Collectors.toList());
    }

    // Test-only constructor (package-private)
    SudokuServiceImpl(List<HintStrategy> strategies) {
        this.generator = new PuzzleGenerator(new Random());
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
        List<List<Integer>> grid = generator.generate(normalised);
        return new PuzzleResponse(grid, normalised);
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
}
