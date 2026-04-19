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

import static com.sudoku.domain.SudokuConstants.MAX_DIGIT;
import static com.sudoku.domain.SudokuConstants.MIN_DIGIT;
import static com.sudoku.puzzle.hint.Difficulty.HARD;

/**
 * Detects triples of digits that, within a unit, appear as candidates only within
 * the same three cells — allowing all other candidates to be removed from those cells.
 *
 * Like a Hidden Pair but extended to three digits and three cells; the pattern is
 * "hidden" because the three cells each typically carry extra pencil marks that
 * obscure the triple until the full unit is scanned.
 */
@ApplicationScoped
public class HiddenTripleStrategy implements HintStrategy {

    private static final String NAME = "Hidden Triple";
    private static final String SLUG = "hidden-triple";

    @Override
    public String getName() { return NAME; }

    @Override
    public String getSlug() { return SLUG; }

    @Override
    public Difficulty getDifficulty() { return HARD; }

    @Override
    public int getDifficultyRank() {
        return 80;
    }

    @Override
    public Optional<HintResponse> evaluate(Board board) {
        return UnitScanner.scanAll(board, this::checkUnit);
    }

    private Optional<HintResponse> checkUnit(List<Cell> unit, UnitScanner.UnitContext ctx) {
        String unitType = ctx.type();
        int unitIndex = ctx.index();
        List<Cell> emptyCells = unit.stream().filter(Cell::isEmpty).toList();

        for (int d1 = MIN_DIGIT; d1 <= MAX_DIGIT; d1++) {
            for (int d2 = d1 + 1; d2 <= MAX_DIGIT; d2++) {
                for (int d3 = d2 + 1; d3 <= MAX_DIGIT; d3++) {
                    final int fd1 = d1, fd2 = d2, fd3 = d3;
                    List<Cell> cellsWithAny = emptyCells.stream()
                            .filter(c -> c.candidates().contains(fd1)
                                    || c.candidates().contains(fd2)
                                    || c.candidates().contains(fd3))
                            .toList();

                    if (cellsWithAny.size() != 3) continue;

                    // Each digit must appear in at least one cell — digits with zero occurrences
                    // are already placed elsewhere in the unit and don't form a valid hidden triple.
                    boolean d1present = cellsWithAny.stream().anyMatch(c -> c.candidates().contains(fd1));
                    boolean d2present = cellsWithAny.stream().anyMatch(c -> c.candidates().contains(fd2));
                    boolean d3present = cellsWithAny.stream().anyMatch(c -> c.candidates().contains(fd3));
                    if (!d1present || !d2present || !d3present) continue;

                    List<CoordinateCandidate> eliminations = new ArrayList<>();
                    for (Cell cell : cellsWithAny) {
                        for (int cand : cell.candidates()) {
                            if (cand != d1 && cand != d2 && cand != d3) {
                                eliminations.add(new CoordinateCandidate(cell.row(), cell.col(), cand));
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
                    List<CoordinateCandidate> focusCandidates = new ArrayList<>();
                    for (Cell cell : cellsWithAny) {
                        for (int digit : new int[]{fd1, fd2, fd3}) {
                            if (cell.candidates().contains(digit)) {
                                focusCandidates.add(new CoordinateCandidate(cell.row(), cell.col(), digit));
                            }
                        }
                    }
                    return Optional.of(new HintResponse(
                            NAME,
                            SLUG,
                            getDifficulty(),
                            getDifficultyRank(),
                            "Three digits are collectively confined to exactly three cells within a unit.",
                            unitType + " " + unitIndex + ": digits " + d1 + ", " + d2 + " and " + d3
                                    + " are confined to cells " + focusCells + ".",
                            "All other candidates can be removed from those three cells.",
                            List.of(new Coordinate(cA.row(), cA.col()),
                                    new Coordinate(cB.row(), cB.col()),
                                    new Coordinate(cC.row(), cC.col())),
                            eliminations,
                            List.of(),
                            focusCandidates
                    ));
                }
            }
        }
        return Optional.empty();
    }
}
