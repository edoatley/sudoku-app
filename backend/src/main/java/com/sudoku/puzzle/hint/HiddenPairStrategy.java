package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.CandidateElimination;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class HiddenPairStrategy implements HintStrategy {

    @Override
    public int getDifficultyRank() {
        return 70;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getRow(i), "Row", i);
            if (hint.isPresent()) return hint;
        }
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getColumn(i), "Column", i);
            if (hint.isPresent()) return hint;
        }
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getBlock(i), "Block", i);
            if (hint.isPresent()) return hint;
        }
        return Optional.empty();
    }

    private Optional<HintResponse> checkUnit(List<Cell> unit, String unitType, int unitIndex) {
        List<Cell> emptyCells = new ArrayList<>();
        for (Cell cell : unit) {
            if (cell.isEmpty()) emptyCells.add(cell);
        }

        for (int d1 = 1; d1 <= 9; d1++) {
            for (int d2 = d1 + 1; d2 <= 9; d2++) {
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

                List<CandidateElimination> eliminations = new ArrayList<>();
                for (Cell cell : List.of(cellA, cellB)) {
                    for (int cand : cell.candidates()) {
                        if (cand != d1 && cand != d2) {
                            eliminations.add(new CandidateElimination(cell.row(), cell.col(), cand));
                        }
                    }
                }

                if (eliminations.isEmpty()) continue;

                int rA = cellA.row(), cA = cellA.col();
                int rB = cellB.row(), cB = cellB.col();
                HintResponse hint = new HintResponse(
                        "Hidden Pair",
                        "hidden-pair",
                        "medium",
                        getDifficultyRank(),
                        "Two digits appear as candidates in exactly the same two cells within a unit.",
                        unitType + " " + unitIndex + ": digits " + d1 + " and " + d2
                                + " are confined to cells (" + rA + "," + cA + ") and (" + rB + "," + cB + ").",
                        "All other candidates can be removed from cells (" + rA + "," + cA + ") and (" + rB + "," + cB + ").",
                        List.of(new Coordinate(rA, cA), new Coordinate(rB, cB)),
                        eliminations,
                        List.of(),
                        List.of(
                                new CandidateElimination(rA, cA, d1), new CandidateElimination(rA, cA, d2),
                                new CandidateElimination(rB, cB, d1), new CandidateElimination(rB, cB, d2)
                        )
                );
                return Optional.of(hint);
            }
        }
        return Optional.empty();
    }
}
