package com.sudoku.game;

import com.sudoku.dto.GameState;
import com.sudoku.dto.GameUpdateRequest;

public interface GameService {

    GameState createGame(String difficulty);

    GameState loadGame(String gameId);

    void updateGame(String gameId, GameUpdateRequest request);
}
