package com.sudoku.puzzle.hint;

import com.sudoku.domain.Board;
import com.sudoku.dto.HintResponse;

import java.util.Optional;

/**
 * Contract for a single Sudoku hint technique.
 *
 * Each implementation represents one solving strategy and is responsible for
 * declaring its own identity (name, slug, difficulty, rank) alongside its
 * evaluation logic. The hint engine discovers all strategies via CDI injection
 * and orders them by rank before trying each in turn.
 */
public interface HintStrategy {
    Optional<HintResponse> evaluate(Board board);
    int getDifficultyRank();
    Difficulty getDifficulty();
    String getName();
    String getSlug();
}
