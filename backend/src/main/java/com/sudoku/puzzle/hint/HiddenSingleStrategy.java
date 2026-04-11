package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.CoordinateCandidate;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.sudoku.domain.SudokuConstants.MAX_DIGIT;
import static com.sudoku.domain.SudokuConstants.MIN_DIGIT;
import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

/**
 * Finds digits that appear as a candidate in exactly one cell within a unit,
 * meaning that cell must contain the digit regardless of its other candidates.
 *
 * A Hidden Single is "hidden" because the forcing cell may still have several
 * pencil marks — only by scanning the whole unit does it become clear that no
 * other cell can hold this digit.
 */
@ApplicationScoped
public class HiddenSingleStrategy implements HintStrategy {

    @Override
    public int getDifficultyRank() {
        return 40;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        for (int i = 0; i < UNIT_SIZE; i++) {
            Optional<HintResponse> hint = checkUnit(board.getRow(i), "Row", i + 1);
            if (hint.isPresent()) return hint;
        }
        for (int i = 0; i < UNIT_SIZE; i++) {
            Optional<HintResponse> hint = checkUnit(board.getColumn(i), "Column", i + 1);
            if (hint.isPresent()) return hint;
        }
        for (int i = 0; i < UNIT_SIZE; i++) {
            Optional<HintResponse> hint = checkUnit(board.getBlock(i), "Block", i + 1);
            if (hint.isPresent()) return hint;
        }
        return Optional.empty();
    }

    private Optional<HintResponse> checkUnit(List<Cell> unit, String unitType, int unitNumber) {
        for (int digit = MIN_DIGIT; digit <= MAX_DIGIT; digit++) {
            List<Cell> matches = new ArrayList<>();
            for (Cell cell : unit) {
                if (cell.isEmpty() && cell.candidates().contains(digit)) {
                    matches.add(cell);
                }
            }
            if (matches.size() == 1) {
                Cell found = matches.get(0);
                int r = found.row();
                int c = found.col();

                List<Coordinate> highlights = new ArrayList<>(9);
                for (Cell cell : unit) {
                    highlights.add(new Coordinate(cell.row(), cell.col()));
                }

                return Optional.of(new HintResponse(
                        "Hidden Single",
                        "hidden-single",
                        "easy",
                        getDifficultyRank(),
                        "A digit appears as a candidate in exactly one cell within a unit.",
                        unitType + " " + unitNumber + " has digit " + digit + " as a candidate in only one cell.",
                        "Cell (" + r + ", " + c + ") must be " + digit + ".",
                        highlights,
                        List.of(),
                        List.of(new ActionableCell(r, c, digit)),
                        List.of(new CoordinateCandidate(r, c, digit))
                ));
            }
        }
        return Optional.empty();
    }
}
