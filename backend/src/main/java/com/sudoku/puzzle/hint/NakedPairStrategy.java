package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.CoordinateCandidate;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;
import static com.sudoku.puzzle.hint.Difficulty.EASY;

/**
 * Detects pairs of cells in a unit that share exactly the same two candidates,
 * allowing those two digits to be eliminated from all other cells in the unit.
 *
 * Because the two digits must occupy the two cells in some order, they are
 * unavailable to any other cell in the row, column, or block — a deduction that
 * often unlocks further simplifications in the surrounding units.
 */
@ApplicationScoped
public class NakedPairStrategy implements HintStrategy {

    private static final String NAME = "Naked Pair";
    private static final String SLUG = "naked-pair";

    @Override
    public String getName() { return NAME; }

    @Override
    public String getSlug() { return SLUG; }

    // @spec HE-BE-012
    @Override
    public Difficulty getDifficulty() { return EASY; }

    @Override
    public int getDifficultyRank() {
        return 30;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        // Scan rows 0-8
        for (int i = 0; i < UNIT_SIZE; i++) {
            Optional<HintResponse> hint = checkUnit(board.getRow(i));
            if (hint.isPresent()) return hint;
        }
        // Scan columns 0-8
        for (int i = 0; i < UNIT_SIZE; i++) {
            Optional<HintResponse> hint = checkUnit(board.getColumn(i));
            if (hint.isPresent()) return hint;
        }
        // Scan blocks 0-8
        for (int i = 0; i < UNIT_SIZE; i++) {
            Optional<HintResponse> hint = checkUnit(board.getBlock(i));
            if (hint.isPresent()) return hint;
        }
        return Optional.empty();
    }

    private Optional<HintResponse> checkUnit(List<Cell> unit) {
        List<Cell> twoCandCells = new ArrayList<>();
        for (Cell cell : unit) {
            if (cell.isEmpty() && cell.candidates().size() == 2) {
                twoCandCells.add(cell);
            }
        }

        for (int i = 0; i < twoCandCells.size(); i++) {
            for (int j = i + 1; j < twoCandCells.size(); j++) {
                Cell cellA = twoCandCells.get(i);
                Cell cellB = twoCandCells.get(j);
                if (!cellA.candidates().equals(cellB.candidates())) continue;

                Set<Integer> pairDigits = cellA.candidates();
                Iterator<Integer> it = pairDigits.iterator();
                int d1 = it.next();
                int d2 = it.next();

                List<CoordinateCandidate> eliminations = new ArrayList<>();
                for (Cell cell : unit) {
                    if (cell == cellA || cell == cellB || !cell.isEmpty()) continue;
                    if (cell.candidates().contains(d1)) {
                        eliminations.add(new CoordinateCandidate(cell.row(), cell.col(), d1));
                    }
                    if (cell.candidates().contains(d2)) {
                        eliminations.add(new CoordinateCandidate(cell.row(), cell.col(), d2));
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
                        "Two cells in the same unit share exactly the same two candidates.",
                        "Cells (" + rA + "," + cA + ") and (" + rB + "," + cB + ") both have only " + d1 + " and " + d2 + ".",
                        d1 + " and " + d2 + " can be eliminated from all other cells in the unit.",
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
