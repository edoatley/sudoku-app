package com.sudoku.puzzle;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.sudoku.domain.SudokuConstants.BOX_SIZE;
import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

/**
 * Generates valid Sudoku puzzles with a unique solution.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Fill a blank board using randomised recursive backtracking to produce a complete solution.</li>
 *   <li>Randomly remove clues one at a time, checking after each removal that the puzzle still has
 *       exactly one solution. If removal creates ambiguity, restore the clue and try a different cell.</li>
 *   <li>Stop when the target clue count for the requested difficulty is reached.</li>
 * </ol>
 *
 * <p>All heavy per-request work is done in {@link #generate}; the class itself is stateless and
 * SnapStart-safe (no static mutable state).
 */
@ApplicationScoped
public class PuzzleGenerator {

    /** Target number of given clues per difficulty level. */
    private static final int CLUES_EASY   = 36;
    private static final int CLUES_MEDIUM = 30;
    private static final int CLUES_HARD   = 25;
    private static final int CLUES_EXPERT = 22;

    private final Random random;

    /** CDI no-arg constructor — {@link Random} is initialised at startup for SnapStart readiness. */
    public PuzzleGenerator() {
        this.random = new Random();
    }

    /** Package-private constructor for tests that require a seeded {@link Random}. */
    PuzzleGenerator(Random random) {
        this.random = random;
    }

    /**
     * Result of puzzle generation, carrying both the puzzle (with holes) and its unique solution.
     */
    public record PuzzleResult(List<List<Integer>> puzzle, List<List<Integer>> solution) {}

    /**
     * Generates a puzzle grid for the requested difficulty.
     *
     * @param difficulty one of "easy", "medium", "hard", "expert" (case-insensitive)
     * @return a {@link PuzzleResult} containing the puzzle (zeros for empty cells) and its solution
     */
    public PuzzleResult generate(String difficulty) {
        int targetClues = switch (difficulty.toLowerCase()) {
            case "easy"   -> CLUES_EASY;
            case "hard"   -> CLUES_HARD;
            case "expert" -> CLUES_EXPERT;
            default       -> CLUES_MEDIUM;
        };

        int[][] solution = new int[9][9];
        fillBoard(solution);

        int[][] puzzle = copyGrid(solution);
        digHoles(puzzle, targetClues);

        return new PuzzleResult(toImmutableList(puzzle), toImmutableList(solution));
    }

    /**
     * Attempts to solve the given puzzle grid using backtracking.
     *
     * @param puzzle a 9×9 grid with zeros for empty cells
     * @return an {@link Optional} containing the solved grid if exactly one solution exists,
     *         or {@link Optional#empty()} if the puzzle has no solution or contains contradictions
     */
    public Optional<List<List<Integer>>> solveGrid(List<List<Integer>> puzzle) {
        int[][] grid = toArray(puzzle);
        if (!fillBoard(grid)) {
            return Optional.empty();
        }
        return Optional.of(toImmutableList(grid));
    }

    // -------------------------------------------------------------------------
    // Step 1: fill a blank board with a valid complete solution
    // -------------------------------------------------------------------------

    private boolean fillBoard(int[][] grid) {
        int[] cell = findEmpty(grid);
        if (cell == null) return true; // board is complete

        int row = cell[0];
        int col = cell[1];

        List<Integer> digits = shuffled(1, 9);
        for (int digit : digits) {
            if (isPlaceable(grid, row, col, digit)) {
                grid[row][col] = digit;
                if (fillBoard(grid)) return true;
                grid[row][col] = 0;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Step 2: dig holes until the target clue count is reached
    // -------------------------------------------------------------------------

    private void digHoles(int[][] grid, int targetClues) {
        List<int[]> positions = allPositions();
        Collections.shuffle(positions, random);

        int filled = 81;
        for (int[] pos : positions) {
            if (filled <= targetClues) break;

            int row = pos[0];
            int col = pos[1];
            int backup = grid[row][col];

            grid[row][col] = 0;

            if (countSolutions(grid, 0) == 1) {
                filled--;
            } else {
                grid[row][col] = backup; // restore — would create ambiguity
            }
        }
    }

    // -------------------------------------------------------------------------
    // Uniqueness checker: counts solutions up to a limit of 2
    // -------------------------------------------------------------------------

    /**
     * Returns the number of solutions for {@code grid}, stopping as soon as 2 are found.
     * This makes the uniqueness check O(fast-fail) rather than exhaustive.
     */
    private int countSolutions(int[][] grid, int count) {
        if (count >= 2) return count; // short-circuit

        int[] cell = findEmpty(grid);
        if (cell == null) return count + 1; // found a complete solution

        int row = cell[0];
        int col = cell[1];

        for (int digit = 1; digit <= 9; digit++) {
            if (isPlaceable(grid, row, col, digit)) {
                grid[row][col] = digit;
                count = countSolutions(grid, count);
                grid[row][col] = 0;
                if (count >= 2) break;
            }
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int[] findEmpty(int[][] grid) {
        for (int r = 0; r < UNIT_SIZE; r++) {
            for (int c = 0; c < UNIT_SIZE; c++) {
                if (grid[r][c] == 0) return new int[]{r, c};
            }
        }
        return null;
    }

    private boolean isPlaceable(int[][] grid, int row, int col, int digit) {
        // row check
        for (int c = 0; c < UNIT_SIZE; c++) {
            if (grid[row][c] == digit) return false;
        }
        // column check
        for (int r = 0; r < UNIT_SIZE; r++) {
            if (grid[r][col] == digit) return false;
        }
        // 3×3 block check
        int startRow = (row / BOX_SIZE) * BOX_SIZE;
        int startCol = (col / BOX_SIZE) * BOX_SIZE;
        for (int r = startRow; r < startRow + BOX_SIZE; r++) {
            for (int c = startCol; c < startCol + BOX_SIZE; c++) {
                if (grid[r][c] == digit) return false;
            }
        }
        return true;
    }

    private List<Integer> shuffled(int from, int to) {
        List<Integer> list = new ArrayList<>(to - from + 1);
        for (int i = from; i <= to; i++) list.add(i);
        Collections.shuffle(list, random);
        return list;
    }

    private List<int[]> allPositions() {
        List<int[]> positions = new ArrayList<>(UNIT_SIZE * UNIT_SIZE);
        for (int r = 0; r < UNIT_SIZE; r++) {
            for (int c = 0; c < UNIT_SIZE; c++) {
                positions.add(new int[]{r, c});
            }
        }
        return positions;
    }

    private int[][] copyGrid(int[][] source) {
        int[][] copy = new int[UNIT_SIZE][UNIT_SIZE];
        for (int r = 0; r < UNIT_SIZE; r++) {
            System.arraycopy(source[r], 0, copy[r], 0, UNIT_SIZE);
        }
        return copy;
    }

    private int[][] toArray(List<List<Integer>> grid) {
        int[][] arr = new int[UNIT_SIZE][UNIT_SIZE];
        for (int r = 0; r < UNIT_SIZE; r++) {
            for (int c = 0; c < UNIT_SIZE; c++) {
                arr[r][c] = grid.get(r).get(c);
            }
        }
        return arr;
    }

    private List<List<Integer>> toImmutableList(int[][] grid) {
        List<List<Integer>> result = new ArrayList<>(UNIT_SIZE);
        for (int r = 0; r < UNIT_SIZE; r++) {
            List<Integer> row = new ArrayList<>(UNIT_SIZE);
            for (int c = 0; c < UNIT_SIZE; c++) {
                row.add(grid[r][c]);
            }
            result.add(Collections.unmodifiableList(row));
        }
        return Collections.unmodifiableList(result);
    }
}
