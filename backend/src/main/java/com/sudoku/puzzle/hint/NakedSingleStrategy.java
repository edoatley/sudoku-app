package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class NakedSingleStrategy implements HintStrategy {

    @Override
    public int getDifficultyRank() {
        return 20;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                Cell cell = board.getCell(r, c);
                if (cell.isEmpty() && cell.candidates().size() == 1) {
                    int digit = cell.candidates().iterator().next();
                    HintResponse hint = new HintResponse(
                            "Naked Single",
                            "naked-single",
                            "easy",
                            "A cell has been reduced to exactly one possible candidate.",
                            "Cell (" + r + ", " + c + ") has had every other digit eliminated.",
                            "Cell (" + r + ", " + c + ") must be " + digit + ".",
                            List.of(new Coordinate(r, c)),
                            List.of(),
                            List.of(new ActionableCell(r, c, digit))
                    );
                    return Optional.of(hint);
                }
            }
        }
        return Optional.empty();
    }
}
