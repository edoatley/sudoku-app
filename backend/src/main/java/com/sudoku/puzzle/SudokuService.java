package com.sudoku.puzzle;

import com.sudoku.dto.BoardRequest;
import com.sudoku.dto.CandidatesResponse;
import com.sudoku.dto.HintResponse;
import com.sudoku.dto.PuzzleResponse;
import com.sudoku.dto.ValidationResponse;

import java.util.List;
import java.util.Optional;

public interface SudokuService {
    PuzzleResponse generatePuzzle(String difficulty);
    ValidationResponse validatePuzzle(BoardRequest request);
    Optional<HintResponse> getHint(BoardRequest request);
    CandidatesResponse getCandidates(BoardRequest request);
    Optional<List<List<Integer>>> solveGrid(List<List<Integer>> puzzle);
}
