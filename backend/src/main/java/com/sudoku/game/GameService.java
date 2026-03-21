package com.sudoku.game;

import com.sudoku.dto.GameState;
import com.sudoku.dto.GameUpdateRequest;

public interface GameService {

    GameState createGame(String userId, String difficulty);

    GameState loadGame(String userId, String gameId);

    void updateGame(String userId, String gameId, GameUpdateRequest request);
}
