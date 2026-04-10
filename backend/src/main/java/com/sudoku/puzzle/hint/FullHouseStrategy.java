package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.ActionableCell;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class FullHouseStrategy implements HintStrategy {

    @Override
    public int getDifficultyRank() {
        return 10;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        // Scan rows 0-8
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getRow(i), "Row", i + 1);
            if (hint.isPresent()) return hint;
        }
        // Scan columns 0-8
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getColumn(i), "Column", i + 1);
            if (hint.isPresent()) return hint;
        }
        // Scan blocks 0-8
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getBlock(i), "Block", i + 1);
            if (hint.isPresent()) return hint;
        }
        return Optional.empty();
    }

    private Optional<HintResponse> checkUnit(List<Cell> unit, String unitType, int unitNumber) {
        List<Cell> emptyCells = new ArrayList<>();
        Set<Integer> placed = new HashSet<>();
        for (Cell cell : unit) {
            if (cell.isEmpty()) {
                emptyCells.add(cell);
            } else {
                placed.add(cell.value());
            }
        }
        if (emptyCells.size() != 1) return Optional.empty();

        Cell emptyCell = emptyCells.get(0);
        int missingDigit = -1;
        for (int d = 1; d <= 9; d++) {
            if (!placed.contains(d)) {
                missingDigit = d;
                break;
            }
        }

        int r = emptyCell.row();
        int c = emptyCell.col();
        HintResponse hint = new HintResponse(
                "Full House",
                "full-house",
                "easy",
                getDifficultyRank(),
                "One unit has only a single empty cell remaining.",
                unitType + " " + unitNumber + " has 8 of 9 cells filled.",
                "Cell (" + r + ", " + c + ") must be " + missingDigit + ".",
                List.of(new Coordinate(r, c)),
                List.of(),
                List.of(new ActionableCell(r, c, missingDigit))
        );
        return Optional.of(hint);
    }
}
