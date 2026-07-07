package com.sudoku.player;

import com.sudoku.player.web.PlayerUpdateRequest;
import com.sudoku.player.web.PlayerProfile;
public interface PlayerService {

    PlayerProfile getOrCreateProfile(String userId, String email, String displayName);

    PlayerProfile updateProfile(String userId, PlayerUpdateRequest request);
}
