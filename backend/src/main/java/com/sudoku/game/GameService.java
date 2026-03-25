package com.sudoku.game;

import com.sudoku.dto.GameState;
import com.sudoku.dto.GameUpdateRequest;

import java.util.List;

public interface GameService {

    GameState createGame(String userId, String difficulty);

    GameState createGameFromExistingGrid(String userId, List<List<Integer>> originalGrid);

    GameState loadGame(String userId, String gameId);

    void updateGame(String userId, String gameId, GameUpdateRequest request);
}
