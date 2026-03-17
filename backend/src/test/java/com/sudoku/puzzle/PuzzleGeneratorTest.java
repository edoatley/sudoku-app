package com.sudoku.puzzle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PuzzleGeneratorTest {

    /** Fixed seed so tests are reproducible. */
    private final PuzzleGenerator generator = new PuzzleGenerator(new Random(42));

    // ---- Structure & validity ----

    @ParameterizedTest
    @ValueSource(strings = {"easy", "medium", "hard", "expert"})
    void generate_producesValidNineByNineGrid(String difficulty) {
        List<List<Integer>> grid = generator.generate(difficulty);

        assertNotNull(grid);
        assertEquals(9, grid.size(), "grid must have 9 rows");
        for (int r = 0; r < 9; r++) {
            List<Integer> row = grid.get(r);
            assertEquals(9, row.size(), "row " + r + " must have 9 columns");
            for (int c = 0; c < 9; c++) {
                int val = row.get(c);
                assertTrue(val >= 0 && val <= 9,
                        "Cell (" + r + "," + c + ") value " + val + " out of range");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"easy", "medium", "hard", "expert"})
    void generate_cluesContainNoConflicts(String difficulty) {
        List<List<Integer>> grid = generator.generate(difficulty);

        // Rows
        for (int r = 0; r < 9; r++) {
            assertNoConflicts(cluesIn(grid, rowIndices(r)), "row " + r);
        }
        // Columns
        for (int c = 0; c < 9; c++) {
            assertNoConflicts(cluesIn(grid, colIndices(c)), "column " + c);
        }
        // 3×3 blocks
        for (int b = 0; b < 9; b++) {
            assertNoConflicts(cluesIn(grid, blockIndices(b)), "block " + b);
        }
    }

    // ---- Clue counts ----

    @Test
    void generate_easy_hasAtLeastExpectedClues() {
        List<List<Integer>> grid = generator.generate("easy");
        assertTrue(countClues(grid) >= 36,
                "Easy should have ≥36 clues, got " + countClues(grid));
    }

    @Test
    void generate_medium_hasAtLeastExpectedClues() {
        List<List<Integer>> grid = generator.generate("medium");
        assertTrue(countClues(grid) >= 30,
                "Medium should have ≥30 clues, got " + countClues(grid));
    }

    @Test
    void generate_hard_hasAtLeastExpectedClues() {
        List<List<Integer>> grid = generator.generate("hard");
        assertTrue(countClues(grid) >= 25,
                "Hard should have ≥25 clues, got " + countClues(grid));
    }

    @Test
    void generate_expert_hasAtLeastExpectedClues() {
        List<List<Integer>> grid = generator.generate("expert");
        assertTrue(countClues(grid) >= 22,
                "Expert should have ≥22 clues, got " + countClues(grid));
    }

    @Test
    void generate_unknownDifficulty_fallsBackToMedium() {
        List<List<Integer>> grid = generator.generate("unknown");
        assertTrue(countClues(grid) >= 30,
                "Unknown difficulty should fall back to medium (≥30 clues)");
    }

    // ---- Uniqueness ----

    @ParameterizedTest
    @ValueSource(strings = {"easy", "medium", "hard", "expert"})
    void generate_puzzleHasUniqueSolution(String difficulty) {
        List<List<Integer>> grid = generator.generate(difficulty);
        int[][] puzzle = toArray(grid);
        assertEquals(1, countSolutions(puzzle, 0),
                difficulty + " puzzle must have exactly one solution");
    }

    // ---- Reproducibility ----

    @Test
    void generate_sameRandomSeed_producesSameGrid() {
        PuzzleGenerator g1 = new PuzzleGenerator(new Random(99));
        PuzzleGenerator g2 = new PuzzleGenerator(new Random(99));
        assertEquals(g1.generate("medium"), g2.generate("medium"),
                "Same seed must produce the same puzzle");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int countClues(List<List<Integer>> grid) {
        int count = 0;
        for (List<Integer> row : grid) {
            for (int val : row) {
                if (val != 0) count++;
            }
        }
        return count;
    }

    private void assertNoConflicts(List<Integer> clues, String unitName) {
        Set<Integer> seen = new HashSet<>();
        for (int v : clues) {
            assertFalse(seen.contains(v), unitName + " has duplicate digit " + v);
            seen.add(v);
        }
    }

    private List<Integer> cluesIn(List<List<Integer>> grid, int[][] indices) {
        List<Integer> result = new java.util.ArrayList<>();
        for (int[] rc : indices) {
            int v = grid.get(rc[0]).get(rc[1]);
            if (v != 0) result.add(v);
        }
        return result;
    }

    private int[][] rowIndices(int r) {
        int[][] idx = new int[9][2];
        for (int c = 0; c < 9; c++) { idx[c][0] = r; idx[c][1] = c; }
        return idx;
    }

    private int[][] colIndices(int c) {
        int[][] idx = new int[9][2];
        for (int r = 0; r < 9; r++) { idx[r][0] = r; idx[r][1] = c; }
        return idx;
    }

    private int[][] blockIndices(int b) {
        int startRow = (b / 3) * 3;
        int startCol = (b % 3) * 3;
        int[][] idx = new int[9][2];
        int i = 0;
        for (int r = startRow; r < startRow + 3; r++) {
            for (int c = startCol; c < startCol + 3; c++) {
                idx[i][0] = r; idx[i][1] = c; i++;
            }
        }
        return idx;
    }

    private int[][] toArray(List<List<Integer>> grid) {
        int[][] arr = new int[9][9];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                arr[r][c] = grid.get(r).get(c);
            }
        }
        return arr;
    }

    /** Counts solutions up to 2 (same algorithm used internally by PuzzleGenerator). */
    private int countSolutions(int[][] grid, int count) {
        if (count >= 2) return count;
        int[] cell = findEmpty(grid);
        if (cell == null) return count + 1;
        int row = cell[0], col = cell[1];
        for (int d = 1; d <= 9; d++) {
            if (isPlaceable(grid, row, col, d)) {
                grid[row][col] = d;
                count = countSolutions(grid, count);
                grid[row][col] = 0;
                if (count >= 2) break;
            }
        }
        return count;
    }

    private int[] findEmpty(int[][] grid) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (grid[r][c] == 0) return new int[]{r, c};
            }
        }
        return null;
    }

    private boolean isPlaceable(int[][] grid, int row, int col, int digit) {
        for (int c = 0; c < 9; c++) if (grid[row][c] == digit) return false;
        for (int r = 0; r < 9; r++) if (grid[r][col] == digit) return false;
        int sr = (row / 3) * 3, sc = (col / 3) * 3;
        for (int r = sr; r < sr + 3; r++) {
            for (int c = sc; c < sc + 3; c++) {
                if (grid[r][c] == digit) return false;
            }
        }
        return true;
    }
}
