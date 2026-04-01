package com.sudoku.game;

import com.sudoku.dto.GameState;
import com.sudoku.dto.GameUpdateRequest;

import java.util.List;
import java.util.Optional;

public interface GameService {

    GameState createGame(String userId, String difficulty);

    GameState createGameFromExistingGrid(String userId, List<List<Integer>> originalGrid);

    GameState loadGame(String userId, String gameId);

    Optional<GameState> findInProgress(String userId);

    void updateGame(String userId, String gameId, GameUpdateRequest request);
}
