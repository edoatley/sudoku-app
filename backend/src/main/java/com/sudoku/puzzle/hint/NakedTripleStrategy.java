package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.CandidateElimination;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@ApplicationScoped
public class NakedTripleStrategy implements HintStrategy {

    @Override
    public int getDifficultyRank() {
        return 60;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getRow(i), "Row " + i);
            if (hint.isPresent()) return hint;
        }
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getColumn(i), "Column " + i);
            if (hint.isPresent()) return hint;
        }
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getBlock(i), "Block " + i);
            if (hint.isPresent()) return hint;
        }
        return Optional.empty();
    }

    private Optional<HintResponse> checkUnit(List<Cell> unit, String unitLabel) {
        List<Cell> emptyCells = new ArrayList<>();
        for (Cell cell : unit) {
            if (cell.isEmpty() && cell.candidates().size() >= 1 && cell.candidates().size() <= 3) {
                emptyCells.add(cell);
            }
        }

        int n = emptyCells.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    Cell cellA = emptyCells.get(i);
                    Cell cellB = emptyCells.get(j);
                    Cell cellC = emptyCells.get(k);

                    Set<Integer> union = new HashSet<>();
                    union.addAll(cellA.candidates());
                    union.addAll(cellB.candidates());
                    union.addAll(cellC.candidates());

                    if (union.size() != 3) continue;

                    List<CandidateElimination> eliminations = new ArrayList<>();
                    for (Cell cell : unit) {
                        if (cell == cellA || cell == cellB || cell == cellC || !cell.isEmpty()) continue;
                        for (int digit : union) {
                            if (cell.candidates().contains(digit)) {
                                eliminations.add(new CandidateElimination(cell.row(), cell.col(), digit));
                            }
                        }
                    }

                    if (eliminations.isEmpty()) continue;

                    Set<Integer> sortedDigits = new TreeSet<>(union);
                    List<Integer> digits = new ArrayList<>(sortedDigits);
                    int d1 = digits.get(0), d2 = digits.get(1), d3 = digits.get(2);

                    String focus = unitLabel + ": cells (" + cellA.row() + "," + cellA.col() + "), ("
                            + cellB.row() + "," + cellB.col() + "), ("
                            + cellC.row() + "," + cellC.col() + ") share only candidates {"
                            + d1 + ", " + d2 + ", " + d3 + "}.";
                    String reveal = "Digits " + d1 + ", " + d2 + ", and " + d3
                            + " can be removed from all other cells in " + unitLabel.toLowerCase() + ".";

                    List<CandidateElimination> focusCandidates = new ArrayList<>();
                    for (Cell cell : List.of(cellA, cellB, cellC)) {
                        for (int digit : union) {
                            if (cell.candidates().contains(digit)) {
                                focusCandidates.add(new CandidateElimination(cell.row(), cell.col(), digit));
                            }
                        }
                    }
                    HintResponse hint = new HintResponse(
                            "Naked Triple",
                            "naked-triple",
                            "medium",
                            getDifficultyRank(),
                            "Three cells in a unit collectively contain only three candidates.",
                            focus,
                            reveal,
                            List.of(
                                    new Coordinate(cellA.row(), cellA.col()),
                                    new Coordinate(cellB.row(), cellB.col()),
                                    new Coordinate(cellC.row(), cellC.col())
                            ),
                            eliminations,
                            List.of(),
                            focusCandidates
                    );
                    return Optional.of(hint);
                }
            }
        }
        return Optional.empty();
    }
}
