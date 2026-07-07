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

import static com.sudoku.domain.SudokuConstants.MAX_DIGIT;
import static com.sudoku.domain.SudokuConstants.MIN_DIGIT;
import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;
import static com.sudoku.puzzle.hint.BoardUtils.candidateColumnsInRow;
import static com.sudoku.puzzle.hint.BoardUtils.candidateRowsInColumn;
import static com.sudoku.puzzle.hint.Difficulty.HARD;

/**
 * Detects the X-Wing pattern, where a digit is confined to exactly two cells in
 * each of two rows (or columns) and those cells share the same two columns (or rows).
 *
 * The pattern guarantees the digit must occupy one of the two crossing positions in
 * each base line, which eliminates it from all other cells in the cover lines. X-Wing
 * is the conceptual foundation for the "fish" family of techniques and the direct
 * predecessor of Swordfish.
 */
@ApplicationScoped
public class XWingStrategy implements HintStrategy {

    private static final String NAME = "X-Wing";
    private static final String SLUG = "x-wing";

    @Override
    public String getName() { return NAME; }

    @Override
    public String getSlug() { return SLUG; }

    @Override
    public Difficulty getDifficulty() { return HARD; }

    @Override
    public int getDifficultyRank() {
        return 90;
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
     * Row-based: find two rows where the digit is a candidate in exactly the same two columns.
     * Eliminate the digit from all other cells in those two columns.
     */
    private Optional<HintResponse> checkRowBased(Board board, int digit) {
        // For each row, collect the columns where digit is a candidate (only rows with exactly 2)
        List<int[]> rowsWith2 = new ArrayList<>(); // each entry: {row, col1, col2}
        for (int r = 0; r < UNIT_SIZE; r++) {
            List<Integer> cols = candidateColumnsInRow(board, r, digit);
            if (cols.size() == 2) {
                rowsWith2.add(new int[]{r, cols.get(0), cols.get(1)});
            }
        }

        for (int i = 0; i < rowsWith2.size(); i++) {
            for (int j = i + 1; j < rowsWith2.size(); j++) {
                int[] rowA = rowsWith2.get(i);
                int[] rowB = rowsWith2.get(j);
                if (rowA[1] != rowB[1] || rowA[2] != rowB[2]) continue;

                int r1 = rowA[0], r2 = rowB[0], c1 = rowA[1], c2 = rowA[2];

                List<CoordinateCandidate> eliminations = new ArrayList<>();
                for (int r = 0; r < UNIT_SIZE; r++) {
                    if (r == r1 || r == r2) continue;
                    Cell cellC1 = board.getCell(r, c1);
                    Cell cellC2 = board.getCell(r, c2);
                    if (cellC1.isEmpty() && cellC1.candidates().contains(digit)) {
                        eliminations.add(new CoordinateCandidate(r, c1, digit));
                    }
                    if (cellC2.isEmpty() && cellC2.candidates().contains(digit)) {
                        eliminations.add(new CoordinateCandidate(r, c2, digit));
                    }
                }

                if (eliminations.isEmpty()) continue;

                return Optional.of(new HintResponse(
                        NAME,
                        SLUG,
                        getDifficulty(),
                        getDifficultyRank(),
                        "A digit appears in exactly two cells in each of two rows, and those cells share the same two columns.",
                        "Digit " + digit + " in rows " + r1 + " and " + r2
                                + " is confined to columns " + c1 + " and " + c2 + ".",
                        "Digit " + digit + " can be removed from all other cells in columns " + c1 + " and " + c2 + ".",
                        List.of(new Coordinate(r1, c1), new Coordinate(r1, c2),
                                new Coordinate(r2, c1), new Coordinate(r2, c2)),
                        eliminations,
                        List.of(),
                        List.of(new CoordinateCandidate(r1, c1, digit), new CoordinateCandidate(r1, c2, digit),
                                new CoordinateCandidate(r2, c1, digit), new CoordinateCandidate(r2, c2, digit))
                ));
            }
        }
        return Optional.empty();
    }

    /**
     * Column-based: find two columns where the digit is a candidate in exactly the same two rows.
     * Eliminate the digit from all other cells in those two rows.
     */
    private Optional<HintResponse> checkColumnBased(Board board, int digit) {
        List<int[]> colsWith2 = new ArrayList<>(); // each entry: {col, row1, row2}
        for (int c = 0; c < UNIT_SIZE; c++) {
            List<Integer> rows = candidateRowsInColumn(board, c, digit);
            if (rows.size() == 2) {
                colsWith2.add(new int[]{c, rows.get(0), rows.get(1)});
            }
        }

        for (int i = 0; i < colsWith2.size(); i++) {
            for (int j = i + 1; j < colsWith2.size(); j++) {
                int[] colA = colsWith2.get(i);
                int[] colB = colsWith2.get(j);
                if (colA[1] != colB[1] || colA[2] != colB[2]) continue;

                int c1 = colA[0], c2 = colB[0], r1 = colA[1], r2 = colA[2];

                List<CoordinateCandidate> eliminations = new ArrayList<>();
                for (int c = 0; c < UNIT_SIZE; c++) {
                    if (c == c1 || c == c2) continue;
                    Cell cellR1 = board.getCell(r1, c);
                    Cell cellR2 = board.getCell(r2, c);
                    if (cellR1.isEmpty() && cellR1.candidates().contains(digit)) {
                        eliminations.add(new CoordinateCandidate(r1, c, digit));
                    }
                    if (cellR2.isEmpty() && cellR2.candidates().contains(digit)) {
                        eliminations.add(new CoordinateCandidate(r2, c, digit));
                    }
                }

                if (eliminations.isEmpty()) continue;

                return Optional.of(new HintResponse(
                        NAME,
                        SLUG,
                        getDifficulty(),
                        getDifficultyRank(),
                        "A digit appears in exactly two cells in each of two columns, and those cells share the same two rows.",
                        "Digit " + digit + " in columns " + c1 + " and " + c2
                                + " is confined to rows " + r1 + " and " + r2 + ".",
                        "Digit " + digit + " can be removed from all other cells in rows " + r1 + " and " + r2 + ".",
                        List.of(new Coordinate(r1, c1), new Coordinate(r1, c2),
                                new Coordinate(r2, c1), new Coordinate(r2, c2)),
                        eliminations,
                        List.of(),
                        List.of(new CoordinateCandidate(r1, c1, digit), new CoordinateCandidate(r1, c2, digit),
                                new CoordinateCandidate(r2, c1, digit), new CoordinateCandidate(r2, c2, digit))
                ));
            }
        }
        return Optional.empty();
    }
}
