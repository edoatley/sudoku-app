package com.sudoku.leaderboard;

import com.sudoku.dto.LeaderboardResponse;

// @spec LT-API-001
public interface LeaderboardService {

    LeaderboardResponse getLeaderboard();
}
