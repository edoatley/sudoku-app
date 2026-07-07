package com.sudoku.player;

import com.sudoku.player.persistence.PlayerItem;
import com.sudoku.player.web.PlayerProfile;
import java.util.List;
import java.util.Optional;

public interface PlayerRepository {

    Optional<PlayerProfile> findById(String userId);

    PlayerProfile upsert(PlayerProfile profile);

    // @spec LT-BE-014 — used by LeaderboardServiceImpl to join display names onto aggregate items
    List<PlayerItem> findAll();

    // @spec SC-RL-002, SC-RL-005 — atomically increments the monthly token counter, resetting when the month changes
    void incrementCoachTokens(String userId, long tokensUsed, String currentMonth);
}
