package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.CoordinateCandidate;
import com.sudoku.dto.Coordinate;
import com.sudoku.dto.HintResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.sudoku.domain.SudokuConstants.BOX_SIZE;
import static com.sudoku.domain.SudokuConstants.MAX_DIGIT;
import static com.sudoku.domain.SudokuConstants.MIN_DIGIT;
import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

/**
 * Detects when all occurrences of a digit within a block are confined to a single
 * row or column, allowing the digit to be eliminated from the rest of that line
 * outside the block.
 *
 * Because the digit must be placed somewhere in the block, and all its candidates
 * fall on one line, whichever cell within the block takes the digit will occupy
 * that line — so the digit cannot appear in any other cell on the same line.
 */
@ApplicationScoped
public class PointingPairStrategy implements HintStrategy {

    @Override
    public int getDifficultyRank() {
        return 50;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        for (int blockIndex = 0; blockIndex < UNIT_SIZE; blockIndex++) {
            List<Cell> blockCells = board.getBlock(blockIndex);
            int blockRowStart = (blockIndex / BOX_SIZE) * BOX_SIZE;
            int blockColStart = (blockIndex % BOX_SIZE) * BOX_SIZE;
            int blockRowEnd = blockRowStart + BOX_SIZE - 1;
            int blockColEnd = blockColStart + BOX_SIZE - 1;

            for (int digit = MIN_DIGIT; digit <= MAX_DIGIT; digit++) {
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
                    List<CoordinateCandidate> eliminations = new ArrayList<>();
                    for (Cell cell : board.getRow(firstRow)) {
                        if (cell.isEmpty()
                                && cell.candidates().contains(digit)
                                && (cell.col() < blockColStart || cell.col() > blockColEnd)) {
                            eliminations.add(new CoordinateCandidate(cell.row(), cell.col(), digit));
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
                    List<CoordinateCandidate> eliminations = new ArrayList<>();
                    for (Cell cell : board.getColumn(firstCol)) {
                        if (cell.isEmpty()
                                && cell.candidates().contains(digit)
                                && (cell.row() < blockRowStart || cell.row() > blockRowEnd)) {
                            eliminations.add(new CoordinateCandidate(cell.row(), cell.col(), digit));
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

    private HintResponse buildHint(List<Cell> pointingCells, List<CoordinateCandidate> eliminations,
                                   int digit, String lineDescription, int blockIndex) {
        List<Coordinate> highlights = new ArrayList<>();
        List<CoordinateCandidate> focusCandidates = new ArrayList<>();
        for (Cell cell : pointingCells) {
            highlights.add(new Coordinate(cell.row(), cell.col()));
            focusCandidates.add(new CoordinateCandidate(cell.row(), cell.col(), digit));
        }
        return new HintResponse(
                "Pointing Pair",
                "pointing-pair",
                "medium",
                getDifficultyRank(),
                "Within a block, all candidates for a digit are confined to one row or column.",
                "Block " + blockIndex + ": digit " + digit + " is confined to " + lineDescription + ".",
                "Digit " + digit + " can be removed from the rest of " + lineDescription + " outside block " + blockIndex + ".",
                highlights,
                eliminations,
                List.of(),
                focusCandidates
        );
    }
}
