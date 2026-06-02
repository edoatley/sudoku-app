package com.sudoku.player;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository {

    Optional<PlayerProfile> findById(String userId);

    PlayerProfile upsert(PlayerProfile profile);

    // @spec LT-BE-014 — used by LeaderboardServiceImpl to join display names onto aggregate items
    List<PlayerItem> findAll();
}
