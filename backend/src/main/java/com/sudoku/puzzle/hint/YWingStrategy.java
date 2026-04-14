package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.CoordinateCandidate;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;
import static com.sudoku.puzzle.hint.BoardUtils.isVisible;
import static com.sudoku.puzzle.hint.Difficulty.HARD;

/**
 * Detects the Y-Wing pattern, where a pivot cell with exactly two candidates sees
 * two pincer cells that together force an elimination via a shared candidate.
 *
 * If the pivot has candidates {A, B}, one pincer has {A, C}, and the other has {B, C},
 * then any cell visible to both pincers cannot contain C — because whichever value the
 * pivot takes, at least one pincer must be C, and that pincer sees the target cell.
 */
@ApplicationScoped
public class YWingStrategy implements HintStrategy {

    private static final String NAME = "Y-Wing";
    private static final String SLUG = "y-wing";

    @Override
    public String getName() { return NAME; }

    @Override
    public String getSlug() { return SLUG; }

    @Override
    public Difficulty getDifficulty() { return HARD; }

    @Override
    public int getDifficultyRank() {
        return 110;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        List<Cell> biValueCells = new ArrayList<>();
        for (int r = 0; r < UNIT_SIZE; r++) {
            for (int c = 0; c < UNIT_SIZE; c++) {
                Cell cell = board.getCell(r, c);
                if (cell.isEmpty() && cell.candidates().size() == 2) {
                    biValueCells.add(cell);
                }
            }
        }

        for (Cell pivot : biValueCells) {
            List<Integer> pivotCands = new ArrayList<>(pivot.candidates());
            int a = pivotCands.get(0), b = pivotCands.get(1);

            // Find pincers visible to pivot with exactly 2 candidates sharing exactly one with pivot
            List<Cell> pincerCandidates = new ArrayList<>();
            for (Cell other : biValueCells) {
                if (other == pivot) continue;
                if (!isVisible(pivot, other)) continue;
                Set<Integer> cands = other.candidates();
                boolean hasA = cands.contains(a), hasB = cands.contains(b);
                if (hasA ^ hasB) { // shares exactly one of A or B
                    pincerCandidates.add(other);
                }
            }

            // Try every pair of pincers
            for (int i = 0; i < pincerCandidates.size(); i++) {
                for (int j = i + 1; j < pincerCandidates.size(); j++) {
                    Cell p1 = pincerCandidates.get(i);
                    Cell p2 = pincerCandidates.get(j);

                    // p1 shares A with pivot → p1 has {A, C}; p2 shares B with pivot → p2 has {B, C}
                    // OR p1 shares B with pivot → p1 has {B, C}; p2 shares A with pivot → p2 has {A, C}
                    // In both cases C must be the same in p1 and p2
                    int c1SharedWithPivot = sharedCandidate(pivot, p1, a, b);
                    int c2SharedWithPivot = sharedCandidate(pivot, p2, a, b);
                    if (c1SharedWithPivot == c2SharedWithPivot) continue; // both share same → not a valid Y-Wing pair

                    // The "other" candidate in each pincer
                    int cFromP1 = otherCandidate(p1, c1SharedWithPivot);
                    int cFromP2 = otherCandidate(p2, c2SharedWithPivot);
                    if (cFromP1 != cFromP2) continue; // C must match

                    int c = cFromP1;

                    // Find cells visible to both p1 and p2 that have C as a candidate
                    List<CoordinateCandidate> eliminations = new ArrayList<>();
                    for (int r = 0; r < UNIT_SIZE; r++) {
                        for (int col = 0; col < UNIT_SIZE; col++) {
                            Cell target = board.getCell(r, col);
                            if (target == pivot || target == p1 || target == p2) continue;
                            if (!target.isEmpty()) continue;
                            if (!target.candidates().contains(c)) continue;
                            if (isVisible(p1, target) && isVisible(p2, target)) {
                                eliminations.add(new CoordinateCandidate(r, col, c));
                            }
                        }
                    }

                    if (eliminations.isEmpty()) continue;

                    List<CoordinateCandidate> focusCandidates = new ArrayList<>();
                    for (Cell focusCell : List.of(pivot, p1, p2)) {
                        for (int cand : focusCell.candidates()) {
                            focusCandidates.add(new CoordinateCandidate(focusCell.row(), focusCell.col(), cand));
                        }
                    }
                    return Optional.of(new HintResponse(
                            NAME,
                            SLUG,
                            getDifficulty(),
                            getDifficultyRank(),
                            "A pivot cell with two candidates sees two pincers that together force an elimination.",
                            "Pivot (" + pivot.row() + "," + pivot.col() + ") has {" + a + "," + b + "}; "
                                    + "pincers (" + p1.row() + "," + p1.col() + ") with " + p1.candidates()
                                    + " and (" + p2.row() + "," + p2.col() + ") with " + p2.candidates()
                                    + " share candidate " + c + ".",
                            "Digit " + c + " can be removed from any cell that sees both pincers.",
                            List.of(new Coordinate(pivot.row(), pivot.col()),
                                    new Coordinate(p1.row(), p1.col()),
                                    new Coordinate(p2.row(), p2.col())),
                            eliminations,
                            List.of(),
                            focusCandidates
                    ));
                }
            }
        }
        return Optional.empty();
    }

    /** Returns which of {a, b} the given cell shares with the pivot (the one the cell contains). */
    private int sharedCandidate(Cell pivot, Cell pincer, int a, int b) {
        return pincer.candidates().contains(a) ? a : b;
    }

    /** Returns the candidate in the cell that is not the given one. */
    private int otherCandidate(Cell cell, int known) {
        for (int cand : cell.candidates()) {
            if (cand != known) return cand;
        }
        throw new IllegalStateException("Cell has only one candidate");
    }

}
