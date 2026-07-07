package com.sudoku.leaderboard;

import com.sudoku.leaderboard.web.LeaderboardResponse;

// @spec LT-API-001
public interface LeaderboardService {

    LeaderboardResponse getLeaderboard();
}
