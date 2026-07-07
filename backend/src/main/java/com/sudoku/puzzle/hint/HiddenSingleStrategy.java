package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.puzzle.web.ActionableCell;
import com.sudoku.puzzle.web.CoordinateCandidate;
import com.sudoku.puzzle.web.Coordinate;
import com.sudoku.puzzle.web.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.sudoku.domain.SudokuConstants.MAX_DIGIT;
import static com.sudoku.domain.SudokuConstants.MIN_DIGIT;
import static com.sudoku.puzzle.hint.Difficulty.EASY;

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

    private static final String NAME = "Hidden Single";
    private static final String SLUG = "hidden-single";

    @Override
    public String getName() { return NAME; }

    @Override
    public String getSlug() { return SLUG; }

    @Override
    public Difficulty getDifficulty() { return EASY; }

    @Override
    public int getDifficultyRank() {
        return 40;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        return UnitScanner.scanAll(board, this::checkUnit);
    }

    private Optional<HintResponse> checkUnit(List<Cell> unit, UnitScanner.UnitContext ctx) {
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
                        NAME,
                        SLUG,
                        getDifficulty(),
                        getDifficultyRank(),
                        "A digit appears as a candidate in exactly one cell within a unit.",
                        ctx.label() + " has digit " + digit + " as a candidate in only one cell.",
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
