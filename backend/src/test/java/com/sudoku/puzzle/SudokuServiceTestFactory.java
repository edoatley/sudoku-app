package com.sudoku.puzzle;

import com.sudoku.puzzle.hint.HintStrategy;

import java.util.Comparator;
import java.util.List;

/**
 * Test-only factory for constructing {@link SudokuServiceImpl} instances with a
 * controlled set of strategies, bypassing CDI discovery.
 *
 * <p>Keeping this in {@code src/test/java} ensures no test-only construction paths
 * exist in production code.
 */
public final class SudokuServiceTestFactory {

    private SudokuServiceTestFactory() {}

    /**
     * Creates a {@link SudokuServiceImpl} with the given strategies sorted by rank,
     * using a real {@link PuzzleGenerator}.
     */
    public static SudokuServiceImpl withStrategies(List<HintStrategy> strategies) {
        List<HintStrategy> sorted = strategies.stream()
                .sorted(Comparator.comparingInt(HintStrategy::getDifficultyRank))
                .toList();
        return new SudokuServiceImpl(new PuzzleGenerator(), sorted);
    }

    /**
     * Creates a {@link SudokuServiceImpl} with no strategies — useful for tests that
     * only exercise validation or candidate computation.
     */
    public static SudokuServiceImpl withNoStrategies() {
        return new SudokuServiceImpl(new PuzzleGenerator(), List.of());
    }
}
