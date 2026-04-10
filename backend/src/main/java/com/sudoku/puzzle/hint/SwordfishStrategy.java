package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.CandidateElimination;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

@ApplicationScoped
public class SwordfishStrategy implements HintStrategy {

    @Override
    public int getDifficultyRank() {
        return 100;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        for (int digit = 1; digit <= 9; digit++) {
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

        for (int r = 0; r < 9; r++) {
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

                    List<CandidateElimination> eliminations = new ArrayList<>();
                    for (int r = 0; r < 9; r++) {
                        if (r == r1 || r == r2 || r == r3) continue;
                        for (int c : cols) {
                            Cell cell = board.getCell(r, c);
                            if (cell.isEmpty() && cell.candidates().contains(digit)) {
                                eliminations.add(new CandidateElimination(r, c, digit));
                            }
                        }
                    }

                    if (eliminations.isEmpty()) continue;

                    return Optional.of(new HintResponse(
                            "Swordfish",
                            "swordfish",
                            "hard",
                            getDifficultyRank(),
                            "A digit appears in 2–3 cells across three rows, and those cells span exactly three columns.",
                            "Digit " + digit + " in rows " + r1 + ", " + r2 + " and " + r3
                                    + " is confined to columns " + c1 + ", " + c2 + " and " + c3 + ".",
                            "Digit " + digit + " can be removed from all other cells in columns "
                                    + c1 + ", " + c2 + " and " + c3 + ".",
                            highlights,
                            eliminations,
                            List.of()
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

        for (int c = 0; c < 9; c++) {
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

                    List<CandidateElimination> eliminations = new ArrayList<>();
                    for (int c = 0; c < 9; c++) {
                        if (c == c1 || c == c2 || c == c3) continue;
                        for (int r : rows) {
                            Cell cell = board.getCell(r, c);
                            if (cell.isEmpty() && cell.candidates().contains(digit)) {
                                eliminations.add(new CandidateElimination(r, c, digit));
                            }
                        }
                    }

                    if (eliminations.isEmpty()) continue;

                    return Optional.of(new HintResponse(
                            "Swordfish",
                            "swordfish",
                            "hard",
                            getDifficultyRank(),
                            "A digit appears in 2–3 cells across three columns, and those cells span exactly three rows.",
                            "Digit " + digit + " in columns " + c1 + ", " + c2 + " and " + c3
                                    + " is confined to rows " + r1 + ", " + r2 + " and " + r3 + ".",
                            "Digit " + digit + " can be removed from all other cells in rows "
                                    + r1 + ", " + r2 + " and " + r3 + ".",
                            highlights,
                            eliminations,
                            List.of()
                    ));
                }
            }
        }
        return Optional.empty();
    }

    private List<Integer> candidateColumnsInRow(Board board, int row, int digit) {
        List<Integer> cols = new ArrayList<>();
        for (int c = 0; c < 9; c++) {
            Cell cell = board.getCell(row, c);
            if (cell.isEmpty() && cell.candidates().contains(digit)) cols.add(c);
        }
        return cols;
    }

    private List<Integer> candidateRowsInColumn(Board board, int col, int digit) {
        List<Integer> rows = new ArrayList<>();
        for (int r = 0; r < 9; r++) {
            Cell cell = board.getCell(r, col);
            if (cell.isEmpty() && cell.candidates().contains(digit)) rows.add(r);
        }
        return rows;
    }
}
