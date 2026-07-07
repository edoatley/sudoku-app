package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.puzzle.web.CoordinateCandidate;
import com.sudoku.puzzle.web.Coordinate;
import com.sudoku.puzzle.web.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

import static com.sudoku.domain.SudokuConstants.MAX_DIGIT;
import static com.sudoku.domain.SudokuConstants.MIN_DIGIT;
import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;
import static com.sudoku.puzzle.hint.BoardUtils.candidateColumnsInRow;
import static com.sudoku.puzzle.hint.BoardUtils.candidateRowsInColumn;
import static com.sudoku.puzzle.hint.Difficulty.HARD;

/**
 * Detects the Swordfish pattern, the 3-line generalisation of X-Wing.
 *
 * When a digit appears in 2 or 3 candidate positions across each of three rows
 * and those positions span exactly three columns (or the symmetric column-based case),
 * the digit can be eliminated from all other cells in those three columns. The pattern
 * is an instance of the "fish" family of techniques where N base lines define N cover
 * lines that must collectively absorb all occurrences of the digit.
 */
@ApplicationScoped
public class SwordfishStrategy implements HintStrategy {

    private static final String NAME = "Swordfish";
    private static final String SLUG = "swordfish";

    @Override
    public String getName() { return NAME; }

    @Override
    public String getSlug() { return SLUG; }

    @Override
    public Difficulty getDifficulty() { return HARD; }

    @Override
    public int getDifficultyRank() {
        return 100;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        for (int digit = MIN_DIGIT; digit <= MAX_DIGIT; digit++) {
            Optional<HintResponse> hint = checkRowBased(board, digit);
            if (hint.isPresent()) return hint;
            hint = checkColumnBased(board, digit);
            if (hint.isPresent()) return hint;
        }
        return Optional.empty();
    }

    /**
     * Row-based: find three rows where the digit appears in 2 or 3 columns each,
     * and those columns union to exactly 3 distinct columns.
     * Eliminate the digit from all other cells in those 3 columns.
     */
    private Optional<HintResponse> checkRowBased(Board board, int digit) {
        // For each row, collect columns where digit is a candidate (only rows with 2 or 3 such columns)
        List<List<Integer>> colSets = new ArrayList<>();
        List<Integer> rowIndices = new ArrayList<>();

        for (int r = 0; r < UNIT_SIZE; r++) {
            List<Integer> cols = candidateColumnsInRow(board, r, digit);
            if (cols.size() == 2 || cols.size() == 3) {
                rowIndices.add(r);
                colSets.add(cols);
            }
        }

        for (int i = 0; i < rowIndices.size(); i++) {
            for (int j = i + 1; j < rowIndices.size(); j++) {
                for (int k = j + 1; k < rowIndices.size(); k++) {
                    TreeSet<Integer> union = new TreeSet<>(colSets.get(i));
                    union.addAll(colSets.get(j));
                    union.addAll(colSets.get(k));
                    if (union.size() != 3) continue;

                    int r1 = rowIndices.get(i), r2 = rowIndices.get(j), r3 = rowIndices.get(k);
                    List<Integer> cols = new ArrayList<>(union);
                    int c1 = cols.get(0), c2 = cols.get(1), c3 = cols.get(2);

                    // Highlight cells: defining cells in the three rows
                    List<Coordinate> highlights = new ArrayList<>();
                    for (int r : List.of(r1, r2, r3)) {
                        for (int c : cols) {
                            Cell cell = board.getCell(r, c);
                            if (cell.isEmpty() && cell.candidates().contains(digit)) {
                                highlights.add(new Coordinate(r, c));
                            }
                        }
                    }

                    List<CoordinateCandidate> eliminations = new ArrayList<>();
                    for (int r = 0; r < UNIT_SIZE; r++) {
                        if (r == r1 || r == r2 || r == r3) continue;
                        for (int c : cols) {
                            Cell cell = board.getCell(r, c);
                            if (cell.isEmpty() && cell.candidates().contains(digit)) {
                                eliminations.add(new CoordinateCandidate(r, c, digit));
                            }
                        }
                    }

                    if (eliminations.isEmpty()) continue;

                    List<CoordinateCandidate> focusCandidates = new ArrayList<>();
                    for (Coordinate h : highlights) {
                        focusCandidates.add(new CoordinateCandidate(h.row(), h.col(), digit));
                    }
                    return Optional.of(new HintResponse(
                            NAME,
                            SLUG,
                            getDifficulty(),
                            getDifficultyRank(),
                            "A digit appears in 2–3 cells across three rows, and those cells span exactly three columns.",
                            "Digit " + digit + " in rows " + r1 + ", " + r2 + " and " + r3
                                    + " is confined to columns " + c1 + ", " + c2 + " and " + c3 + ".",
                            "Digit " + digit + " can be removed from all other cells in columns "
                                    + c1 + ", " + c2 + " and " + c3 + ".",
                            highlights,
                            eliminations,
                            List.of(),
                            focusCandidates
                    ));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Column-based: find three columns where the digit appears in 2 or 3 rows each,
     * and those rows union to exactly 3 distinct rows.
     * Eliminate the digit from all other cells in those 3 rows.
     */
    private Optional<HintResponse> checkColumnBased(Board board, int digit) {
        List<List<Integer>> rowSets = new ArrayList<>();
        List<Integer> colIndices = new ArrayList<>();

        for (int c = 0; c < UNIT_SIZE; c++) {
            List<Integer> rows = candidateRowsInColumn(board, c, digit);
            if (rows.size() == 2 || rows.size() == 3) {
                colIndices.add(c);
                rowSets.add(rows);
            }
        }

        for (int i = 0; i < colIndices.size(); i++) {
            for (int j = i + 1; j < colIndices.size(); j++) {
                for (int k = j + 1; k < colIndices.size(); k++) {
                    TreeSet<Integer> union = new TreeSet<>(rowSets.get(i));
                    union.addAll(rowSets.get(j));
                    union.addAll(rowSets.get(k));
                    if (union.size() != 3) continue;

                    int c1 = colIndices.get(i), c2 = colIndices.get(j), c3 = colIndices.get(k);
                    List<Integer> rows = new ArrayList<>(union);
                    int r1 = rows.get(0), r2 = rows.get(1), r3 = rows.get(2);

                    List<Coordinate> highlights = new ArrayList<>();
                    for (int c : List.of(c1, c2, c3)) {
                        for (int r : rows) {
                            Cell cell = board.getCell(r, c);
                            if (cell.isEmpty() && cell.candidates().contains(digit)) {
                                highlights.add(new Coordinate(r, c));
                            }
                        }
                    }

                    List<CoordinateCandidate> eliminations = new ArrayList<>();
                    for (int c = 0; c < UNIT_SIZE; c++) {
                        if (c == c1 || c == c2 || c == c3) continue;
                        for (int r : rows) {
                            Cell cell = board.getCell(r, c);
                            if (cell.isEmpty() && cell.candidates().contains(digit)) {
                                eliminations.add(new CoordinateCandidate(r, c, digit));
                            }
                        }
                    }

                    if (eliminations.isEmpty()) continue;

                    List<CoordinateCandidate> focusCandidates2 = new ArrayList<>();
                    for (Coordinate h : highlights) {
                        focusCandidates2.add(new CoordinateCandidate(h.row(), h.col(), digit));
                    }
                    return Optional.of(new HintResponse(
                            NAME,
                            SLUG,
                            getDifficulty(),
                            getDifficultyRank(),
                            "A digit appears in 2–3 cells across three columns, and those cells span exactly three rows.",
                            "Digit " + digit + " in columns " + c1 + ", " + c2 + " and " + c3
                                    + " is confined to rows " + r1 + ", " + r2 + " and " + r3 + ".",
                            "Digit " + digit + " can be removed from all other cells in rows "
                                    + r1 + ", " + r2 + " and " + r3 + ".",
                            highlights,
                            eliminations,
                            List.of(),
                            focusCandidates2
                    ));
                }
            }
        }
        return Optional.empty();
    }
}
