package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.CoordinateCandidate;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

/**
 * Finds cells where candidate elimination has left exactly one possible digit,
 * making the value immediately placeable without further reasoning.
 *
 * A Naked Single is the direct result of constraint propagation — a cell forced
 * by its row, column, and block collectively ruling out every digit but one.
 */
@ApplicationScoped
public class NakedSingleStrategy implements HintStrategy {

    @Override
    public int getDifficultyRank() {
        return 20;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        for (int r = 0; r < UNIT_SIZE; r++) {
            for (int c = 0; c < UNIT_SIZE; c++) {
                Cell cell = board.getCell(r, c);
                if (cell.isEmpty() && cell.candidates().size() == 1) {
                    int digit = cell.candidates().iterator().next();
                    HintResponse hint = new HintResponse(
                            "Naked Single",
                            "naked-single",
                            "easy",
                            getDifficultyRank(),
                            "A cell has been reduced to exactly one possible candidate.",
                            "Cell (" + r + ", " + c + ") has had every other digit eliminated.",
                            "Cell (" + r + ", " + c + ") must be " + digit + ".",
                            List.of(new Coordinate(r, c)),
                            List.of(),
                            List.of(new ActionableCell(r, c, digit)),
                            List.of(new CoordinateCandidate(r, c, digit))
                    );
                    return Optional.of(hint);
                }
            }
        }
        return Optional.empty();
    }
}
