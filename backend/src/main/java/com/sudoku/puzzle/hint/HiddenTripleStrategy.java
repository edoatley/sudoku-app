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
public class HiddenTripleStrategy implements HintStrategy {

    @Override
    public int getDifficultyRank() {
        return 80;
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
        List<Cell> emptyCells = unit.stream().filter(Cell::isEmpty).toList();

        for (int d1 = 1; d1 <= 9; d1++) {
            for (int d2 = d1 + 1; d2 <= 9; d2++) {
                for (int d3 = d2 + 1; d3 <= 9; d3++) {
                    final int fd1 = d1, fd2 = d2, fd3 = d3;
                    List<Cell> cellsWithAny = emptyCells.stream()
                            .filter(c -> c.candidates().contains(fd1)
                                    || c.candidates().contains(fd2)
                                    || c.candidates().contains(fd3))
                            .toList();

                    if (cellsWithAny.size() != 3) continue;

                    List<CandidateElimination> eliminations = new ArrayList<>();
                    for (Cell cell : cellsWithAny) {
                        for (int cand : cell.candidates()) {
                            if (cand != d1 && cand != d2 && cand != d3) {
                                eliminations.add(new CandidateElimination(cell.row(), cell.col(), cand));
                            }
                        }
                    }

                    if (eliminations.isEmpty()) continue;

                    Cell cA = cellsWithAny.get(0);
                    Cell cB = cellsWithAny.get(1);
                    Cell cC = cellsWithAny.get(2);
                    String focusCells = "(" + cA.row() + "," + cA.col() + "), ("
                            + cB.row() + "," + cB.col() + "), ("
                            + cC.row() + "," + cC.col() + ")";
                    return Optional.of(new HintResponse(
                            "Hidden Triple",
                            "hidden-triple",
                            "hard",
                            "Three digits are collectively confined to exactly three cells within a unit.",
                            unitType + " " + unitIndex + ": digits " + d1 + ", " + d2 + " and " + d3
                                    + " are confined to cells " + focusCells + ".",
                            "All other candidates can be removed from those three cells.",
                            List.of(new Coordinate(cA.row(), cA.col()),
                                    new Coordinate(cB.row(), cB.col()),
                                    new Coordinate(cC.row(), cC.col())),
                            eliminations,
                            List.of()
                    ));
                }
            }
        }
        return Optional.empty();
    }
}
