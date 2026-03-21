package com.sudoku.player;

import java.util.Optional;

public interface PlayerRepository {

    Optional<PlayerProfile> findById(String userId);

    PlayerProfile upsert(PlayerProfile profile);
}
