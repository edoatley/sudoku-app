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
public class PointingPairStrategy implements HintStrategy {

    @Override
    public int getDifficultyRank() {
        return 50;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        for (int blockIndex = 0; blockIndex < 9; blockIndex++) {
            List<Cell> blockCells = board.getBlock(blockIndex);
            int blockRowStart = (blockIndex / 3) * 3;
            int blockColStart = (blockIndex % 3) * 3;
            int blockRowEnd = blockRowStart + 2;
            int blockColEnd = blockColStart + 2;

            for (int digit = 1; digit <= 9; digit++) {
                List<Cell> candidateCells = new ArrayList<>();
                for (Cell cell : blockCells) {
                    if (cell.isEmpty() && cell.candidates().contains(digit)) {
                        candidateCells.add(cell);
                    }
                }
                if (candidateCells.size() < 2) continue;

                // Check if all candidate cells share the same row
                int firstRow = candidateCells.get(0).row();
                boolean rowLocked = candidateCells.stream().allMatch(c -> c.row() == firstRow);
                if (rowLocked) {
                    List<CandidateElimination> eliminations = new ArrayList<>();
                    for (Cell cell : board.getRow(firstRow)) {
                        if (cell.isEmpty()
                                && cell.candidates().contains(digit)
                                && (cell.col() < blockColStart || cell.col() > blockColEnd)) {
                            eliminations.add(new CandidateElimination(cell.row(), cell.col(), digit));
                        }
                    }
                    if (!eliminations.isEmpty()) {
                        return Optional.of(buildHint(candidateCells, eliminations, digit,
                                "row " + firstRow, blockIndex));
                    }
                }

                // Check if all candidate cells share the same column
                int firstCol = candidateCells.get(0).col();
                boolean colLocked = candidateCells.stream().allMatch(c -> c.col() == firstCol);
                if (colLocked) {
                    List<CandidateElimination> eliminations = new ArrayList<>();
                    for (Cell cell : board.getColumn(firstCol)) {
                        if (cell.isEmpty()
                                && cell.candidates().contains(digit)
                                && (cell.row() < blockRowStart || cell.row() > blockRowEnd)) {
                            eliminations.add(new CandidateElimination(cell.row(), cell.col(), digit));
                        }
                    }
                    if (!eliminations.isEmpty()) {
                        return Optional.of(buildHint(candidateCells, eliminations, digit,
                                "column " + firstCol, blockIndex));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private HintResponse buildHint(List<Cell> pointingCells, List<CandidateElimination> eliminations,
                                   int digit, String lineDescription, int blockIndex) {
        List<Coordinate> highlights = new ArrayList<>();
        for (Cell cell : pointingCells) {
            highlights.add(new Coordinate(cell.row(), cell.col()));
        }
        return new HintResponse(
                "Pointing Pair",
                "pointing-pair",
                "medium",
                "Within a block, all candidates for a digit are confined to one row or column.",
                "Block " + blockIndex + ": digit " + digit + " is confined to " + lineDescription + ".",
                "Digit " + digit + " can be removed from the rest of " + lineDescription + " outside block " + blockIndex + ".",
                highlights,
                eliminations,
                List.of()
        );
    }
}
