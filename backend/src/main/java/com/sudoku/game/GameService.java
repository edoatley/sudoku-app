package com.sudoku.game;

import com.sudoku.domain.Grid;
import com.sudoku.game.web.GameHistoryResponse;
import com.sudoku.game.web.GameState;
import com.sudoku.game.web.GameUpdateRequest;

import java.util.Optional;

// @spec DT-SVC-003
public interface GameService {

    GameState createGame(String userId, String difficulty);

    GameState createGameFromExistingGrid(String userId, Grid originalGrid);

    GameState loadGame(String userId, String gameId);

    Optional<GameState> findInProgress(String userId);

    void updateGame(String userId, String gameId, GameUpdateRequest request);

    // @spec GH-SVC-001
    GameHistoryResponse getGameHistory(String userId, int limit);
}
