package com.sudoku.puzzle;

import com.sudoku.domain.Grid;
import com.sudoku.dto.BoardRequest;
import com.sudoku.dto.CandidatesResponse;
import com.sudoku.dto.PuzzleResponse;
import com.sudoku.dto.ValidationResponse;
import com.sudoku.puzzle.hint.HintResult;

import java.util.Optional;

// @spec DT-SVC-001, DT-SVC-002
public interface SudokuService {
    PuzzleResponse generatePuzzle(String difficulty);
    ValidationResponse validatePuzzle(BoardRequest request);
    HintResult getHint(BoardRequest request);
    CandidatesResponse getCandidates(BoardRequest request);
    Optional<Grid> solveGrid(Grid puzzle);

    /**
     * Returns true if the given grid has exactly one valid solution.
     * Returns false for grids with no solution or multiple solutions.
     */
    boolean hasSingleSolution(Grid grid);
}
