package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.domain.Cell;
import com.sudoku.dto.Coordinate;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.sudoku.domain.SudokuConstants.BOX_SIZE;
import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

/**
 * Stateless board geometry helpers shared by hint strategies that reason about
 * candidate positions across rows, columns, and blocks.
 *
 * These queries sit below the level of any individual solving technique — they
 * answer structural questions about where a digit is still possible in a line or
 * whether two cells influence each other — and are therefore factored out here
 * rather than duplicated inside each strategy or polluting the HintStrategy
 * interface with implementation details.
 */
public final class BoardUtils {

    private BoardUtils() {}

    /**
     * Scans all 27 units (9 rows, 9 columns, 9 blocks) of {@code board} and returns the
     * coordinates of every cell that participates in at least one duplicate within its unit.
     * Empty cells (value 0) are ignored.
     *
     * <p>The returned set preserves insertion order so callers receive a deterministic
     * result without needing to sort.
     */
    public static Set<Coordinate> findDuplicatesInBoard(Board board) {
        Set<Coordinate> errors = new LinkedHashSet<>();
        for (int i = 0; i < UNIT_SIZE; i++) {
            findDuplicatesInUnit(board.getRow(i), errors);
            findDuplicatesInUnit(board.getColumn(i), errors);
            findDuplicatesInUnit(board.getBlock(i), errors);
        }
        return errors;
    }

    private static void findDuplicatesInUnit(List<Cell> unit, Set<Coordinate> errors) {
        Set<Integer> seen = new HashSet<>();
        Set<Integer> duplicates = new HashSet<>();
        for (Cell cell : unit) {
            if (!cell.isEmpty() && !seen.add(cell.value())) {
                duplicates.add(cell.value());
            }
        }
        for (Cell cell : unit) {
            if (!cell.isEmpty() && duplicates.contains(cell.value())) {
                errors.add(new Coordinate(cell.row(), cell.col()));
            }
        }
    }

    /**
     * Returns the column indices in the given row where {@code digit} is an
     * active candidate in an empty cell, in ascending order.
     */
    public static List<Integer> candidateColumnsInRow(Board board, int row, int digit) {
        return IntStream.range(0, UNIT_SIZE)
                .filter(c -> {
                    Cell cell = board.getCell(row, c);
                    return cell.isEmpty() && cell.candidates().contains(digit);
                })
                .boxed()
                .toList();
    }

    /**
     * Returns the row indices in the given column where {@code digit} is an
     * active candidate in an empty cell, in ascending order.
     */
    public static List<Integer> candidateRowsInColumn(Board board, int col, int digit) {
        return IntStream.range(0, UNIT_SIZE)
                .filter(r -> {
                    Cell cell = board.getCell(r, col);
                    return cell.isEmpty() && cell.candidates().contains(digit);
                })
                .boxed()
                .toList();
    }

    /**
     * Returns {@code true} if cells {@code a} and {@code b} are in the same row,
     * column, or 3×3 box — meaning any candidate eliminated from one is visible to
     * (and may therefore also be eliminated from) the other.
     */
    public static boolean isVisible(Cell a, Cell b) {
        if (a.row() == b.row()) return true;
        if (a.col() == b.col()) return true;
        return (a.row() / BOX_SIZE == b.row() / BOX_SIZE)
                && (a.col() / BOX_SIZE == b.col() / BOX_SIZE);
    }
}
