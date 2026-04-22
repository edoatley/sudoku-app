package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.HintResponse;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

/**
 * Iterates all 27 units of a board (9 rows → 9 columns → 9 blocks) and applies a
 * caller-supplied check to each unit, returning the first non-empty result.
 *
 * <p>This removes the identical triple-loop boilerplate that would otherwise appear in
 * every unit-based strategy, leaving each strategy to express only the logic that is
 * unique to its technique.
 *
 * <p>Usage:
 * <pre>{@code
 *   return UnitScanner.scanAll(board, this::checkUnit);
 *
 *   private Optional<HintResponse> checkUnit(List<Cell> unit, UnitScanner.UnitContext ctx) { ... }
 * }</pre>
 */
public final class UnitScanner {

    private UnitScanner() {}

    /**
     * Contextual metadata about the unit currently being scanned.
     *
     * @param type  one of {@code "Row"}, {@code "Column"}, or {@code "Block"}
     * @param index 0-based unit index within its type (0–8)
     * @param label convenience label, e.g. {@code "Row 3"} or {@code "Block 0"}
     */
    public record UnitContext(String type, int index, String label) {}

    /**
     * Scans all 27 units of {@code board} in rows → columns → blocks order,
     * applying {@code checker} to each. Returns the first present result, or
     * {@code Optional.empty()} if no unit yields one.
     */
    public static Optional<HintResponse> scanAll(
            Board board,
            BiFunction<List<Cell>, UnitContext, Optional<HintResponse>> checker) {

        for (int i = 0; i < UNIT_SIZE; i++) {
            Optional<HintResponse> hint = checker.apply(board.getRow(i), new UnitContext("Row", i, "Row " + i));
            if (hint.isPresent()) return hint;
        }
        for (int i = 0; i < UNIT_SIZE; i++) {
            Optional<HintResponse> hint = checker.apply(board.getColumn(i), new UnitContext("Column", i, "Column " + i));
            if (hint.isPresent()) return hint;
        }
        for (int i = 0; i < UNIT_SIZE; i++) {
            Optional<HintResponse> hint = checker.apply(board.getBlock(i), new UnitContext("Block", i, "Block " + i));
            if (hint.isPresent()) return hint;
        }
        return Optional.empty();
    }
}
