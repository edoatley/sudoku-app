package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.CandidateElimination;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class HiddenSingleStrategy implements HintStrategy {

    @Override
    public int getDifficultyRank() {
        return 40;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getRow(i), "Row", i + 1);
            if (hint.isPresent()) return hint;
        }
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getColumn(i), "Column", i + 1);
            if (hint.isPresent()) return hint;
        }
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getBlock(i), "Block", i + 1);
            if (hint.isPresent()) return hint;
        }
        return Optional.empty();
    }

    private Optional<HintResponse> checkUnit(List<Cell> unit, String unitType, int unitNumber) {
        for (int digit = 1; digit <= 9; digit++) {
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
                        List.of(new CandidateElimination(r, c, digit))
                ));
            }
        }
        return Optional.empty();
    }
}
