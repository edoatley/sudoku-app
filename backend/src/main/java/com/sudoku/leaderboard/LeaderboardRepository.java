package com.sudoku.leaderboard;

import java.util.List;

// @spec LT-BE-007
public interface LeaderboardRepository {

    void updateOnSolve(String userId, String difficulty, int elapsedSeconds, int score, String outcome);

    List<LeaderboardItem> findAll();
}
