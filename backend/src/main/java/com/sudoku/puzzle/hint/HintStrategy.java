package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.dto.HintResponse;

import java.util.Optional;

public interface HintStrategy {
    Optional<HintResponse> evaluate(Board board);
    int getDifficultyRank();
}
