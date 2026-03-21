package com.sudoku.player;

public interface PlayerService {

    PlayerProfile getOrCreateProfile(String userId, String email, String displayName);
}
