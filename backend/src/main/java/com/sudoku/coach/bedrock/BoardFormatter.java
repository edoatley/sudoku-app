package com.sudoku.coach.bedrock;

import com.sudoku.domain.Board;

import static com.sudoku.domain.SudokuConstants.BOX_SIZE;
import static com.sudoku.domain.SudokuConstants.UNIT_SIZE;

// @spec SC-BE-004
/**
 * Formats a {@link Board} as a human-readable string for use in LLM prompts.
 *
 * <p>Produces a row-by-row representation with box separators and 1-based row labels,
 * which is substantially clearer to language models than a raw nested-array format.
 *
 * <pre>
 * Row 1: 5 3 _ | _ 7 _ | _ _ _
 * Row 2: 6 _ _ | 1 9 5 | _ _ _
 * Row 3: _ 9 8 | _ _ _ | _ 6 _
 *        ------+-------+------
 * Row 4: 8 _ _ | _ 6 _ | _ _ 3
 * ...
 * </pre>
 */
public final class BoardFormatter {

    private BoardFormatter() {}

    public static String format(Board board) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < UNIT_SIZE; r++) {
            if (r == BOX_SIZE || r == BOX_SIZE * 2) {
                sb.append("       ------+-------+------\n");
            }
            sb.append(String.format("Row %d: ", r + 1));
            for (int c = 0; c < UNIT_SIZE; c++) {
                if (c == BOX_SIZE || c == BOX_SIZE * 2) {
                    sb.append("| ");
                }
                int val = board.getCell(r, c).value();
                sb.append(val == 0 ? "_ " : val + " ");
            }
            sb.append("\n");
        }
        return sb.toString().stripTrailing();
    }
}
