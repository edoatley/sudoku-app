package com.sudoku.game;

import com.sudoku.dto.GameState;
import com.sudoku.dto.GameUpdateRequest;

import java.util.Optional;

public interface GameRepository {

    void save(GameState gameState);

    Optional<GameState> findById(String userId, String gameId);

    Optional<GameState> findInProgress(String userId);

    void update(String userId, String gameId, GameUpdateRequest request);
}
