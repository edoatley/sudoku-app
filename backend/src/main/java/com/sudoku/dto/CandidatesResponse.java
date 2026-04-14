package com.sudoku.dto;

import java.util.List;

/**
 * The full set of pencil-mark candidates for every cell on the board, serialised as a
 * 9×9 nested list.
 *
 * <p>Each inner list contains the sorted digits (1–9) that are still valid for that cell
 * according to standard Sudoku constraints. Cells that already hold a placed value carry
 * an empty list. Sent to the frontend so it can render or auto-populate pencil marks.
 */
public record CandidatesResponse(List<List<List<Integer>>> candidatesGrid) {}
