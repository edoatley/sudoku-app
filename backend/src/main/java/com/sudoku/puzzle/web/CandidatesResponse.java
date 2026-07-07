package com.sudoku.puzzle.web;

import com.sudoku.domain.CandidatesGrid;

// @spec DT-DTO-006

/**
 * The full set of pencil-mark candidates for every cell on the board.
 *
 * <p>Each inner list contains the sorted digits (1–9) that are still valid for that cell
 * according to standard Sudoku constraints. Cells that already hold a placed value carry
 * an empty list. Sent to the frontend so it can render or auto-populate pencil marks.
 */
public record CandidatesResponse(CandidatesGrid candidatesGrid) {}
