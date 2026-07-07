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
import static com.sudoku.puzzle.hint.Difficulty.MEDIUM;

/**
 * Detects pairs of digits that, within a unit, appear as candidates only in the
 * same two cells — allowing all other candidates to be removed from those cells.
 *
 * The pattern is "hidden" because the two cells typically carry additional pencil
 * marks; only the constraint that neither digit can go anywhere else in the unit
 * reveals that the pair is locked to those two cells.
 */
@ApplicationScoped
public class HiddenPairStrategy implements HintStrategy {

    private static final String NAME = "Hidden Pair";
    private static final String SLUG = "hidden-pair";

    @Override
    public String getName() { return NAME; }

    @Override
    public String getSlug() { return SLUG; }

    @Override
    public Difficulty getDifficulty() { return MEDIUM; }

    @Override
    public int getDifficultyRank() {
        return 70;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        return UnitScanner.scanAll(board, this::checkUnit);
    }

    private Optional<HintResponse> checkUnit(List<Cell> unit, UnitScanner.UnitContext ctx) {
        String unitType = ctx.type();
        int unitIndex = ctx.index();
        List<Cell> emptyCells = new ArrayList<>();
        for (Cell cell : unit) {
            if (cell.isEmpty()) emptyCells.add(cell);
        }

        for (int d1 = MIN_DIGIT; d1 <= MAX_DIGIT; d1++) {
            for (int d2 = d1 + 1; d2 <= MAX_DIGIT; d2++) {
                List<Cell> cellsWithD1 = new ArrayList<>();
                List<Cell> cellsWithD2 = new ArrayList<>();
                for (Cell cell : emptyCells) {
                    if (cell.candidates().contains(d1)) cellsWithD1.add(cell);
                    if (cell.candidates().contains(d2)) cellsWithD2.add(cell);
                }

                if (cellsWithD1.size() != 2 || cellsWithD2.size() != 2) continue;
                if (cellsWithD1.get(0) != cellsWithD2.get(0) ||
                        cellsWithD1.get(1) != cellsWithD2.get(1)) continue;

                Cell cellA = cellsWithD1.get(0);
                Cell cellB = cellsWithD1.get(1);

                List<CoordinateCandidate> eliminations = new ArrayList<>();
                for (Cell cell : List.of(cellA, cellB)) {
                    for (int cand : cell.candidates()) {
                        if (cand != d1 && cand != d2) {
                            eliminations.add(new CoordinateCandidate(cell.row(), cell.col(), cand));
                        }
                    }
                }

                if (eliminations.isEmpty()) continue;

                int rA = cellA.row(), cA = cellA.col();
                int rB = cellB.row(), cB = cellB.col();
                HintResponse hint = new HintResponse(
                        NAME,
                        SLUG,
                        getDifficulty(),
                        getDifficultyRank(),
                        "Two digits appear as candidates in exactly the same two cells within a unit.",
                        unitType + " " + unitIndex + ": digits " + d1 + " and " + d2
                                + " are confined to cells (" + rA + "," + cA + ") and (" + rB + "," + cB + ").",
                        "All other candidates can be removed from cells (" + rA + "," + cA + ") and (" + rB + "," + cB + ").",
                        List.of(new Coordinate(rA, cA), new Coordinate(rB, cB)),
                        eliminations,
                        List.of(),
                        List.of(
                                new CoordinateCandidate(rA, cA, d1), new CoordinateCandidate(rA, cA, d2),
                                new CoordinateCandidate(rB, cB, d1), new CoordinateCandidate(rB, cB, d2)
                        )
                );
                return Optional.of(hint);
            }
        }
        return Optional.empty();
    }
}
