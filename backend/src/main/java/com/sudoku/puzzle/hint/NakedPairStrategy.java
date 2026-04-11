package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.CandidateElimination;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class NakedPairStrategy implements HintStrategy {

    @Override
    public int getDifficultyRank() {
        return 30;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        // Scan rows 0-8
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getRow(i));
            if (hint.isPresent()) return hint;
        }
        // Scan columns 0-8
        for (int i = 0; i < 9; i++) {
            Optional<HintResponse> hint = checkUnit(board.getColumn(i));
            if (hint.isPresent()) return hint;
        }
        // Scan blocks 0-8
        for (int i = 0; i < 9; i++) {
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

                List<CandidateElimination> eliminations = new ArrayList<>();
                for (Cell cell : unit) {
                    if (cell == cellA || cell == cellB || !cell.isEmpty()) continue;
                    if (cell.candidates().contains(d1)) {
                        eliminations.add(new CandidateElimination(cell.row(), cell.col(), d1));
                    }
                    if (cell.candidates().contains(d2)) {
                        eliminations.add(new CandidateElimination(cell.row(), cell.col(), d2));
                    }
                }

                if (eliminations.isEmpty()) continue;

                int rA = cellA.row(), cA = cellA.col();
                int rB = cellB.row(), cB = cellB.col();
                HintResponse hint = new HintResponse(
                        "Naked Pair",
                        "naked-pair",
                        "medium",
                        getDifficultyRank(),
                        "Two cells in the same unit share exactly the same two candidates.",
                        "Cells (" + rA + "," + cA + ") and (" + rB + "," + cB + ") both have only " + d1 + " and " + d2 + ".",
                        d1 + " and " + d2 + " can be eliminated from all other cells in the unit.",
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
